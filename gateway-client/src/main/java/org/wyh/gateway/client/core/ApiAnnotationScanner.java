package org.wyh.gateway.client.core;

import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.ProviderConfig;
import org.apache.dubbo.config.spring.ServiceBean;
import org.wyh.common.config.DubboServiceInvoker;
import org.wyh.common.config.HttpServiceInvoker;
import org.wyh.common.config.ServiceDefinition;
import org.wyh.common.config.ServiceInvoker;
import org.wyh.common.constant.BasicConst;
import org.wyh.gateway.client.support.ApiInvoker;
import org.wyh.gateway.client.support.ApiProtocol;
import org.wyh.gateway.client.support.ApiService;
import org.wyh.gateway.client.support.DubboConstants;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * @BelongsProject: my-api-gateway
 * @BelongsPackage: org.wyh.gateway.client.core
 * @Author: wyh
 * @Date: 2024-01-25 14:50
 * @Description: 注解扫描类。用于扫描后台服务类/接口上的@ApiInvoker和@ApiService注解，然后构建相应的服务定义。
                 注意：在实际使用中，客户端模块会被作为外部jar包引入到spring boot应用中。
 */
public class ApiAnnotationScanner {
    /**
     * @BelongsProject: my-api-gateway
     * @BelongsPackage: org.wyh.gateway.client.core
     * @Author: wyh
     * @Date: 2024-01-25 15:02
     * @Description: 静态内部类，用于实现单例模式
     */
    private static class SingletonHolder {
        static final ApiAnnotationScanner INSTANCE = new ApiAnnotationScanner();
    }
    /**
     * @date: 2024-01-25 15:03
     * @description: private修饰的无参构造器，用于实现单例模式
     * @return: null
     */
    private ApiAnnotationScanner() {
    }
    /**
     * @date: 2024-01-25 15:04
     * @description: 获取该类的单例对象
     * @return: org.wyh.gateway.client.core.ApiAnnotationScanner
     */
    public static ApiAnnotationScanner getInstance() {
        return SingletonHolder.INSTANCE;
    }
    /**
     * @date: 2024-01-25 15:56
     * @description: 扫描传入的服务对象，返回相应的服务定义
     * @Param bean: 服务对应的bean对象
     * @Param args: 可变参数，用于扫描dubbo服务时提供ServiceBean对象
     * @return: org.wyh.common.config.ServiceDefinition
     */
    public ServiceDefinition scanner(Object bean, Object... args){
        //获取服务bean的class对象
        Class<?> aClass = bean.getClass();
        //如果该类上没有@ApiService注解，则说明它不是一个需要注册的服务
        if (!aClass.isAnnotationPresent(ApiService.class)) {
            return null;
        }
        //获取注解对象，并从中获取服务的相关信息
        ApiService apiService = aClass.getAnnotation(ApiService.class);
        String serviceId = apiService.serviceId();
        ApiProtocol protocol = apiService.protocol();
        String patternPath = apiService.patternPath();
        String version = apiService.version();
        String serviceDesc = apiService.desc();

        ServiceDefinition serviceDefinition = new ServiceDefinition();
        Map<String, ServiceInvoker> invokerMap = new HashMap<>();
        //获取服务类中的所有方法
        Method[] methods = aClass.getMethods();
        if(methods != null && methods.length > 0){
            //构造服务的方法调用集合Map<String, ServiceInvoker>
            for(Method method : methods){
                //如果该方法上没有@ApiInvoker对象，则说明它不是一个向外暴露的方法
                ApiInvoker apiInvoker = method.getAnnotation(ApiInvoker.class);
                if(apiInvoker == null){
                    continue;
                }
                String path = apiInvoker.path();
                String methodDesc = apiInvoker.desc();
                //根据服务采用的具体协议，构造对应的ServiceInvoker对象。
                switch (protocol) {
                    case HTTP:
                        HttpServiceInvoker httpServiceInvoker = createHttpServiceInvoker(path);
                        httpServiceInvoker.setDesc(methodDesc);
                        invokerMap.put(path, httpServiceInvoker);
                        break;
                    case DUBBO:
                        //如果服务采用的是dubbo协议，则调用该方法时需要额外提供对应的ServiceBean对象
                        ServiceBean<?> serviceBean = (ServiceBean<?>) args[0];
                        DubboServiceInvoker dubboServiceInvoker =
                                createDubboServiceInvoker(path, serviceBean, method);
                        dubboServiceInvoker.setDesc(methodDesc);
                        String dubboVersion = dubboServiceInvoker.getVersion();
                        if (!StringUtils.isBlank(dubboVersion)) {
                            //将版本号修改为dubbo的版本号（以dubbo版本号为主）
                            version = dubboVersion;
                        }
                        invokerMap.put(path, dubboServiceInvoker);
                        break;
                    default:
                        //目前只支持http和dubbo两种协议的服务
                        break;
                }
            }
            //设置服务定义的相关信息
            serviceDefinition.setUniqueId(serviceId + BasicConst.COLON_SEPARATOR + version);
            serviceDefinition.setServiceId(serviceId);
            serviceDefinition.setVersion(version);
            serviceDefinition.setProtocol(protocol.getCode());
            serviceDefinition.setPatternPath(patternPath);
            serviceDefinition.setEnable(true);
            serviceDefinition.setDesc(serviceDesc);
            serviceDefinition.setInvokerMap(invokerMap);

            return serviceDefinition;
        }
        //如果服务类的方法调用为空，则该服务没有意义，返回null
        return null;
    }
    /**
     * @date: 2024-01-25 15:09
     * @description: 构建HttpServiceInvoker对象
     * @Param path: http服务方法调用的路径
     * @return: org.wyh.common.config.HttpServiceInvoker
     */
    private HttpServiceInvoker createHttpServiceInvoker(String path){
        HttpServiceInvoker httpServiceInvoker = new HttpServiceInvoker();
        httpServiceInvoker.setInvokerPath(path);
        return httpServiceInvoker;
    }
    /**
     * @date: 2024-01-25 15:10
     * @description: 构建DubboServiceInvoker对象
     * @Param path: dubbo服务方法调用的路径
     * @Param serviceBean: 每个暴露出去的dubbo服务都会生成一个ServiceBean对象，该对象封装了服务的相关信息
                           "?"通配符代表未知类型，使用"?"通配符可以使泛型接收任何类型的数据。
     * @Param method: Method对象封装了对应方法的相关信息
     * @return: org.wyh.common.config.DubboServiceInvoker
     */
    private DubboServiceInvoker createDubboServiceInvoker(
            String path, ServiceBean<?> serviceBean, Method method){
        DubboServiceInvoker dubboServiceInvoker = new DubboServiceInvoker();
        //设置DubboServiceInvoker实例的相关属性
        dubboServiceInvoker.setInvokerPath(path);
        String methodName = method.getName();
        String registerAddress = serviceBean.getRegistry().getAddress();
        String interfaceClass = serviceBean.getInterface();
        dubboServiceInvoker.setRegisterAddress(registerAddress);
        dubboServiceInvoker.setMethodName(methodName);
        dubboServiceInvoker.setInterfaceClass(interfaceClass);
        //设置“参数类型”的属性值
        String[] parameterTypes = new String[method.getParameterCount()];
        Class<?>[] classes = method.getParameterTypes();
        for (int i = 0; i < classes.length; i++) {
            //参数的类型名用全类名
            parameterTypes[i] = classes[i].getName();
        }
        dubboServiceInvoker.setParameterTypes(parameterTypes);
        /*
         * 下面这段代码的大致意思是：
         * 先通过serviceBean.getTimeout方法获取服务超时时间
         * 若获取失败（或值为0），则通过serviceBean.getProvider方法获取服务提供者的配置类对象providerConfig
         * 再从配置类对象中获取超时时间
         * 若上述过程仍然失败，则将超时时间设为网关系统的预设值
         */
        Integer serviceTimeout = serviceBean.getTimeout();
        if (serviceTimeout == null || serviceTimeout.intValue() == 0) {
            ProviderConfig providerConfig = serviceBean.getProvider();
            if (providerConfig != null) {
                Integer providerTimeout = providerConfig.getTimeout();
                if (providerTimeout == null || providerTimeout.intValue() == 0) {
                    serviceTimeout = DubboConstants.DUBBO_TIMEOUT;
                } else {
                    serviceTimeout = providerTimeout;
                }
            }
        }
        dubboServiceInvoker.setTimeout(serviceTimeout);

        String dubboVersion = serviceBean.getVersion();
        dubboServiceInvoker.setVersion(dubboVersion);
        return dubboServiceInvoker;
    }
}
