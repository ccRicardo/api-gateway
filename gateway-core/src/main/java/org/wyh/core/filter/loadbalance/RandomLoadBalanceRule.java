package org.wyh.core.filter.loadbalance;

import lombok.extern.slf4j.Slf4j;
import org.wyh.common.config.DynamicConfigManager;
import org.wyh.common.config.ServiceInstance;
import org.wyh.common.exception.NotFoundException;
import org.wyh.core.context.GatewayContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import static org.wyh.common.enums.ResponseCode.SERVICE_INSTANCE_NOT_FOUND;

/**
 * @BelongsProject: my-api-gateway
 * @BelongsPackage: org.wyh.core.filter.loadbalance
 * @Author: wyh
 * @Date: 2024-02-21 9:53
 * @Description: 负载均衡策略-随机
 */
@Slf4j
public class RandomLoadBalanceRule implements IGatewayLoadBalanceRule{
    //后端服务id
    private final String serviceId;
    //服务实例集合
    private Set<ServiceInstance> serviceInstanceSet;
    /*
     * 保存服务id与对应的RandomLoadBalanceRule对象。
     * 这么做的目的是避免对同一个serviceId，创建多个重复的RandomLoadBalanceRule对象。
     * 即大幅减少了创建RandomLoadBalanceRule对象的开销。
     */
    private static ConcurrentHashMap<String, RandomLoadBalanceRule> serviceMap = new ConcurrentHashMap<>();
    /**
     * @date: 2024-02-21 14:21
     * @description: 有参构造器，主要负责初始化final修饰的serviceId属性
     * @Param serviceId:
     * @return: null
     */
    public RandomLoadBalanceRule(String serviceId){
        this.serviceId = serviceId;
    }
    /**
     * @date: 2024-02-21 14:22
     * @description: 根据serviceId获取相应的RandomLoadBalanceRule对象。
                     该方法可以避免重复创建RandomLoadBalanceRule对象。
     * @Param serviceId:
     * @return: org.wyh.core.filter.loadbalance.RandomLoadBalanceRule
     */
    public static RandomLoadBalanceRule getInstance(String serviceId){
        RandomLoadBalanceRule loadBalanceRule = serviceMap.get(serviceId);
        if(loadBalanceRule == null){
            loadBalanceRule = new RandomLoadBalanceRule(serviceId);
            //将该serviceId的RandomLoadBalanceRule对象存入serviceMap中，供之后使用。
            serviceMap.put(serviceId, loadBalanceRule);
        }
        return loadBalanceRule;
    }

    @Override
    public ServiceInstance choose(GatewayContext ctx) {
        String serviceId = ctx.getUniqueId();
        //将具体的业务逻辑委托给另一个重载方法
        return choose(serviceId, ctx.isGray());
    }

    @Override
    public ServiceInstance choose(String serviceId, boolean gray) {
        serviceInstanceSet = DynamicConfigManager.getInstance().getServiceInstanceByUniqueId(serviceId, gray);
        if(serviceInstanceSet.isEmpty()){
            log.warn("【负载均衡过滤器】无可用的服务实例: {}",serviceId);
            throw new NotFoundException(SERVICE_INSTANCE_NOT_FOUND);
        }
        //将set转换为list，以便能够按照索引取值
        List<ServiceInstance> serviceInstanceList = new ArrayList<>(serviceInstanceSet);
        //获取一个0（包含）到serviceInstanceList.size()（不包含）之间的随机整数。这种随机数生成方式适用于多线程环境。
        int index = ThreadLocalRandom.current().nextInt(serviceInstanceList.size());
        ServiceInstance serviceInstance = serviceInstanceList.get(index);
        return serviceInstance;
    }
}
