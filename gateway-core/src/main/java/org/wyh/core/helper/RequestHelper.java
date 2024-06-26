package org.wyh.core.helper;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.apache.commons.lang3.StringUtils;
import org.wyh.common.config.DynamicConfigManager;
import org.wyh.common.config.HttpServiceInvoker;
import org.wyh.common.config.Rule;
import org.wyh.common.config.ServiceDefinition;
import org.wyh.common.constant.BasicConst;
import org.wyh.common.constant.GatewayConst;
import org.wyh.common.constant.GatewayProtocol;
import org.wyh.common.exception.ResponseException;
import org.wyh.core.context.GatewayContext;
import org.wyh.core.request.GatewayRequest;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.wyh.common.enums.ResponseCode.PATH_NO_MATCHED;

/**
 * @BelongsProject: my-api-gateway
 * @BelongsPackage: org.wyh.core.helper
 * @Author: wyh
 * @Date: 2024-01-17 9:45
 * @Description: 处理请求对象的辅助类
 */
public class RequestHelper {
    /**
     * @date: 2024-01-17 9:55
     * @description: 构建请求在网关中的上下文信息（GatewayContext对象）
     * @Param request:
     * @Param nettyCtx:
     * @return: org.wyh.core.context.GatewayContext
     */
    public static GatewayContext doContext(FullHttpRequest request, ChannelHandlerContext nettyCtx){
        //构建GatewayRequest请求对象
        GatewayRequest gatewayRequest = doRequest(request, nettyCtx);
        //根据请求对象里的uniqueId，从动态配置管理器中获取服务定义信息
        ServiceDefinition serviceDefinition =
                DynamicConfigManager.getInstance().getServiceDefinition(gatewayRequest.getUniqueId());
        /* 以下是之前写死的代码
        //注意：ServiceDefinition类上加了@Builder注解，所以可以采用以下形式来构建对象
        ServiceDefinition serviceDefinition = ServiceDefinition.builder()
                .serviceId("test-demo")
                .enable(true)
                .version("v1")
                .patternPath("**")
                .envType("dev")
                .protocol(GatewayProtocol.HTTP)
                .build();

         */
        /* todo 这段代码其实没有实际作用。因为本系统最关键的其实是获取服务实例，获取真实的主机地址。
        todo 在这个过程中，方法调用信息其实根本就没有用到，没有任何作用。
        //根据请求对象，获取服务定义中对应的服务（方法）调用的信息
        HttpServiceInvoker serviceInvoker = new HttpServiceInvoker();
        serviceInvoker.setInvokerPath(gatewayRequest.getPath());
        serviceInvoker.setTimeout(500);

         */
        //根据请求对象中的路径，获取相应的规则
        Rule rule = getRule(gatewayRequest, serviceDefinition.getServiceId());

        //构建该请求的GatewayContext上下文对象
        GatewayContext gatewayContext = new GatewayContext(
                serviceDefinition.getProtocol(),
                nettyCtx,
                HttpUtil.isKeepAlive(request),
                gatewayRequest,
                rule,
                0);
        //在负载均衡过滤器中，已经获取了真实的服务实例，并且更新了请求中的modifyHost属性。因此这里不需要再设置。
        //gatewayContext.getRequest().setModifyHost("127.0.0.1:8080");
        return gatewayContext;
    }
    /**
     * @date: 2024-01-17 9:58
     * @description: 构建GatewayRequest请求对象
     * @Param request:
     * @Param nettyCtx:
     * @return: org.wyh.core.request.GatewayRequest
     */
    private static GatewayRequest doRequest(FullHttpRequest request, ChannelHandlerContext nettyCtx){
        /*
         * 以下一系列操作主要是在获取构建GatewayRequest对象需要的参数
         */
        HttpHeaders headers = request.headers();
        String uniqueId = headers.get(GatewayConst.UNIQUE_ID);
        String host = headers.get(HttpHeaderNames.HOST);
        HttpMethod method = request.method();
        String uri = request.uri();
        String clientIp = getClientIp(request, nettyCtx);
        //HttpUtil.getMimeType方法能够获取HttpMessage及其子类的contentType值
        String contentType = HttpUtil.getMimeType(request) == null ? null : HttpUtil.getMimeType(request).toString();
        Charset charset = HttpUtil.getCharset(request, StandardCharsets.UTF_8);

        //根据上述操作获得的参数值，构建GatewayRequest对象
        GatewayRequest gatewayRequest = new GatewayRequest(uniqueId, charset, clientIp,
                host, uri, method, contentType, headers, request);
        return gatewayRequest;
    }
    /**
     * @date: 2024-01-17 9:59
     * @description: 获取客户端ip
     * @Param request:
     * @Param nettyCtx:
     * @return: java.lang.String
     */
    private static String getClientIp(FullHttpRequest request, ChannelHandlerContext nettyCtx){
        /*
         * X-Forwarded-For(XFF)是一个http请求头字段，用于标识通过代理方式连接到服务器的客户端的真实IP地址
         * 例如，如果一个http请求到达服务器之前，经过了三个代理Proxy1、Proxy2、Proxy3，IP分别为IP1、IP2、IP3，
         * 用户真实IP为IP0，那么该请求的X-Forwarded-For字段值应为: IP0, IP1, IP2
         */
        String xForwardedValue = request.headers().get(BasicConst.HTTP_FORWARD_SEPARATOR);
        String clientIp = null;
        //如果客户端通过代理方式连接服务器，那么就需要找到客户端的真实ip
        if(StringUtils.isNotEmpty(xForwardedValue)){
            List<String> values = Arrays.asList(xForwardedValue.split(", "));
            if(values.size() >= 1 && StringUtils.isNotBlank(values.get(0))){
                //第一个值就是客户端的原始ip地址
                clientIp = values.get(0);
            }
        }
        //如果客户端没使用代理，那么直接获取的ip就是其真实ip
        if(clientIp == null){
            InetSocketAddress inetSocketAddress = (InetSocketAddress)nettyCtx.channel().remoteAddress();
            clientIp = inetSocketAddress.getAddress().getHostAddress();
        }
        return clientIp;
    }
    /**
     * @date: 2024-02-20 16:05
     * @description: 根据请求对象中的路径，获取对应的规则
     * @Param gatewayRequest:
     * @Param serviceId:
     * @return: org.wyh.common.config.Rule
     */
    private static Rule getRule(GatewayRequest gatewayRequest, String serviceId){
        //动态配置管理器中的pathRuleMap中的key是由服务id和请求路径构成
        String key = serviceId +"."+ gatewayRequest.getPath();
        Rule rule = DynamicConfigManager.getInstance().getRuleByPath(key);
        if(rule != null){
            return rule;
        }
        //如果上述规则查找步骤（getRuleByPath）失败，则使用另一种方式（getRuleByServiceId）进行查找
        /*
         * stream方法将返回的List<Rule>转换为流
         * filter方法的作用是过滤，具体来说，它会遍历流中的每一个元素，并检查表达式结果是否为真。
         * 只有当结果为真，对应元素才会被保留在流中。
         * findAny方法会返回流中的任意一个元素，通常是第一个元素。
         * orElseThrow方法会在流为空时抛出一个特定的异常。
         * 综上，该段代码的思路就是先根据serviceId找到服务对应的规则集合
         * 然后再根据请求路径的前缀找到匹配的规则
         */
        return DynamicConfigManager.getInstance().getRuleByServiceId(serviceId)
                .stream().filter(r -> gatewayRequest.getPath().startsWith(r.getPrefix()))
                .findAny().orElseThrow(()-> new ResponseException(PATH_NO_MATCHED));
    }
}
