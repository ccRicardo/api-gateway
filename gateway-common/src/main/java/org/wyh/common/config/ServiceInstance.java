package org.wyh.common.config;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

/**
 * @BelongsProject: my-api-gateway
 * @BelongsPackage: org.wyh.common.config
 * @Author: wyh
 * @Date: 2024-01-22 10:50
 * @Description: 服务实例定义类。一个服务定义可能会对应多个服务实例。
 */
@Setter
@Getter
public class ServiceInstance implements Serializable {
    private static final long serialVersionUID = -7559569289189228478L;
    //服务实例id，由ip和port构成
    protected String serviceInstanceId;
    //服务定义的唯一id
    protected String uniqueId;
    //服务实例ip
    protected String ip;
    //服务实例port
    protected int port;
    //标签信息
    protected String tags;
    //权重信息（负载均衡时可能用到）
    protected Integer weight;
    //服务注册时的时间戳
    protected long registerTime;
    //服务实例启用/禁用
    protected boolean enable = true;
    //服务实例的版本号
    protected String version;
    //标识是否为灰度服务
    protected boolean gray;
    /**
     * @date: 2024-01-22 14:23
     * @description: 无参构造器
     * @return: null
     */
    public ServiceInstance(){
        super();
    }
    /**
     * @date: 2024-01-22 14:26
     * @description: 获取服务定义的唯一id，同getUniqueId方法
     * @return: java.lang.String
     */
    public String getAddress(){
        return this.uniqueId;
    }
    /**
     * @date: 2024-01-22 14:24
     * @description: 判断服务实例是否启用，同getEnable方法
     * @return: boolean
     */
    public boolean isEnable(){
        return this.enable;
    }
    @Override
    public boolean equals(Object o) {
        if(this == o) {
            return true;
        }
        if(this == null || getClass() != o.getClass()) {
            return false;
        }
        ServiceInstance serviceInstance = (ServiceInstance)o;
        //根据serviceInstanceId来判断两个服务实例是否相同
        return Objects.equals(serviceInstanceId, serviceInstance.serviceInstanceId);
    }

    @Override
    public int hashCode() {
        //通过serviceInstanceId来生成hashcode
        return Objects.hash(serviceInstanceId);
    }

    @Override
    public String toString() {
        return "ServiceInstance{" +
                "serviceInstanceId='" + serviceInstanceId + '\'' +
                ", uniqueId='" + uniqueId + '\'' +
                ", ip='" + ip + '\'' +
                ", port=" + port +
                ", tags='" + tags + '\'' +
                ", weight=" + weight +
                ", registerTime=" + registerTime +
                ", enable=" + enable +
                ", version='" + version + '\'' +
                ", gray=" + gray +
                '}';
    }
}
