package org.wyh.core.netty.processor;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.toolkit.trace.Trace;
import org.asynchttpclient.Request;
import org.asynchttpclient.Response;
import org.wyh.common.enums.ResponseCode;
import org.wyh.common.exception.BaseException;
import org.wyh.common.exception.ConnectException;
import org.wyh.common.exception.ResponseException;
import org.wyh.core.Config;
import org.wyh.core.ConfigLoader;
import org.wyh.core.context.GatewayContext;
import org.wyh.core.filter.FilterChainFactory;
import org.wyh.core.filter.GatewayFilterChainFactory;
import org.wyh.core.helper.AsyncHttpHelper;
import org.wyh.core.helper.RequestHelper;
import org.wyh.core.helper.ResponseHelper;
import org.wyh.core.request.HttpRequestWrapper;
import org.wyh.core.response.GatewayResponse;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

/**
 * @BelongsProject: my-api-gateway
 * @BelongsPackage: org.wyh.core.netty.processor
 * @Author: wyh
 * @Date: 2024-01-17 9:24
 * @Description: 网关请求处理器的基本实现类（未使用缓冲队列）。
                 负责对netty server接收到的请求进行处理，并写回响应结果。
 */
@Slf4j
public class NettyCoreProcessor implements NettyProcessor{
    //过滤器链工厂类，用于生成过滤器链对象。
    private FilterChainFactory filterChainFactory;
    //静态配置信息
    private Config config;
    /**
     * @date: 2024-03-25 15:05
     * @description: 无参构造器，主要是为了调用init初始化方法
     * @return: null
     */
    public NettyCoreProcessor(Config config){
        this.config = config;
        init();
    }
    /**
     * @date: 2024-01-18 15:11
     * @description: 请求失败时调用（准确来说，是接收异步请求的响应之前出现了异常）。写回异常信息，并且释放连接。
     * @Param nettyCtx:
     * @Param fullRequest:
     * @Param fullResponse:
     * @return: void
     */
    private void doWriteAndRelease(ChannelHandlerContext nettyCtx, FullHttpRequest fullRequest,
                                   FullHttpResponse fullResponse){
        //写回请求失败情况下的响应信息，之后通过ChannelFutureListener.CLOSE关闭连接/channel
        nettyCtx.writeAndFlush(fullResponse)
                .addListener(ChannelFutureListener.CLOSE);
        //将fullRequest对象的引用计数减1。如果该对象引用计数为0，则释放该对象。
        ReferenceCountUtil.release(fullRequest);
    }
    @Trace
    @Override
    public void process(HttpRequestWrapper requestWrapper) {
        ChannelHandlerContext nettyCtx = requestWrapper.getNettyCtx();
        FullHttpRequest fullRequest = requestWrapper.getFullHttpRequest();
        try {
            //获取请求在网关中的上下文对象（GatewayContext对象）
            GatewayContext gatewayContext = RequestHelper.doContext(fullRequest, nettyCtx);
            //执行过滤器链，对网关上下文进行过滤处理，并最终路由到相应的后台服务。
            filterChainFactory.buildFilterChain(gatewayContext).doFilterChain(gatewayContext);
        } catch (BaseException e) {
            //捕获已定义的异常
            log.error("Netty核心处理器出现错误 {} {}", e.getCode().getCode(), e.getCode().getMessage());
            //构建响应对象
            FullHttpResponse fullResponse = ResponseHelper.getHttpResponse(e.getCode());
            //写回异常信息，并且释放连接。
            doWriteAndRelease(nettyCtx, fullRequest, fullResponse);
        } catch (Throwable t) {
            //捕获未定义（未知）异常
            log.error("Netty核心处理器出现未知错误", t);
            //构建响应对象
            FullHttpResponse fullResponse = ResponseHelper.getHttpResponse(ResponseCode.INTERNAL_ERROR);
            //写回异常信息，并且释放连接。
            doWriteAndRelease(nettyCtx, fullRequest, fullResponse);
        }
    }
    @Override
    public void init() {
        //获取过滤器工厂类的唯一实例
        this.filterChainFactory = GatewayFilterChainFactory.getInstance();
    }

    @Override
    public void start() {
        //暂时用不到
    }

    @Override
    public void shutdown() {
        //暂时用不到
    }
}
