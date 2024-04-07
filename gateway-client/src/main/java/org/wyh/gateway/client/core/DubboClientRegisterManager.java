package org.wyh.gateway.client.core;

import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.spring.ServiceBean;
import org.apache.dubbo.config.spring.context.event.ServiceBeanExportedEvent;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.wyh.common.config.ServiceDefinition;
import org.wyh.common.config.ServiceInstance;
import org.wyh.common.constant.BasicConst;
import org.wyh.common.constant.GatewayConst;
import org.wyh.common.utils.NetUtils;
import org.wyh.common.utils.TimeUtil;
import org.wyh.gateway.client.support.ApiProperties;

import java.util.HashSet;
import java.util.Set;

/**
 * @BelongsProject: my-api-gateway
 * @BelongsPackage: org.wyh.gateway.client.core
 * @Author: wyh
 * @Date: 2024-01-29 16:06
 * @Description: dubbo服务的注册管理器，负责dubbo服务的自动化注册。
                 dubbo服务也可以采用Spring MVC的web框架，但具体采用的web框架是什么，这里不必细究。
                 ApplicationListener实现类的onApplicationEvent方法会在事件发生时被spring框架自动调用。
 */
@Slf4j
public class DubboClientRegisterManager extends AbstractClientRegisterManager
        implements ApplicationListener<ApplicationEvent> {
    //记录已经注册过的服务的bean。理论上来说，一个应用可以对应多个服务类。但实际上为了解耦合，一个应用通常只包含一个服务。
    private Set<Object> set= new HashSet<>();
    /**
     * @date: 2024-01-29 16:12
     * @description: 有参构造器，需要传入服务的配置类
     * @Param apiProperties:
     * @return: null
     */
    protected DubboClientRegisterManager(ApiProperties apiProperties){
        super(apiProperties);
    }
    @Override
    public void onApplicationEvent(ApplicationEvent applicationEvent) {
        /*
         * 每个dubbo服务都对应一个ServiceBean，它是服务的配置类，也负责服务的暴露
         * 调用ServiceBean的export方法时（即服务被暴露时），会触发ServiceBeanExportedEvent事件
         * 此时，完成dubbo服务的自动注册
         */
        if(applicationEvent instanceof ServiceBeanExportedEvent){
            try{
                //获取要暴露的dubbo服务的ServiceBean对象
                ServiceBean serviceBean = ((ServiceBeanExportedEvent) applicationEvent).getServiceBean();
                //将具体的注册逻辑委托给doRegisterDubbo方法
                doRegisterDubbo(serviceBean);
            }catch(Exception e){
                log.error("【服务接入模块】dubbo服务注册异常", e);
                throw new RuntimeException(e);
            }
        }else if(applicationEvent instanceof ApplicationStartedEvent){
            //应用启动时，打印相应日志。（该类的自动注册时机与HttpClientRegisterManager的自动注册时机有所不同）
            log.info("【服务接入模块】dubbo服务api启动");
        }
    }
    /**
     * @date: 2024-01-29 16:15
     * @description: 实现具体的自动注册流程
     * @Param serviceBean:
     * @return: void
     */
    private void doRegisterDubbo(ServiceBean serviceBean){
        //ServiceBean是服务的配置类。ServiceBean.getRef方法可以获取对应的服务实现类的bean对象。
        Object bean = serviceBean.getRef();
        //如果该服务已经注册过，则跳过
        if(set.contains(bean)){
            return;
        }
        //调用注解扫描器，扫描服务类上的@ApiService和@ApiInvoker注解，并返回相应的服务定义
        ServiceDefinition serviceDefinition = ApiAnnotationScanner.getInstance().scanner(bean, serviceBean);
        if(serviceDefinition == null){
            return;
        }
        //根据服务配置对象，设置服务定义的部署环境
        serviceDefinition.setEnvType(getApiProperties().getEnv());
        //构造服务实例
        ServiceInstance serviceInstance = new ServiceInstance();
        //由于应用是部署在服务器上的，所以这里获取的是服务器ip
        String localIp = NetUtils.getLocalIp();
        int port = serviceBean.getProtocol().getPort();
        String serviceInstanceId = localIp + BasicConst.COLON_SEPARATOR + port;
        String uniqueId = serviceDefinition.getUniqueId();
        String version = serviceDefinition.getVersion();

        serviceInstance.setServiceInstanceId(serviceInstanceId);
        serviceInstance.setUniqueId(uniqueId);
        serviceInstance.setIp(localIp);
        serviceInstance.setPort(port);
        serviceInstance.setRegisterTime(TimeUtil.currentTimeMillis());
        serviceInstance.setVersion(version);
        serviceInstance.setWeight(GatewayConst.DEFAULT_WEIGHT);
        //将该服务注册到注册中心
        register(serviceDefinition, serviceInstance);
        //将已经注册过的服务bean加入set中，防止重复注册
        set.add(bean);
    }
}
