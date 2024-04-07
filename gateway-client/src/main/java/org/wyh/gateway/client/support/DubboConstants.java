package org.wyh.gateway.client.support;

/**
 * @BelongsProject: my-api-gateway
 * @BelongsPackage: org.wyh.gateway.client.support
 * @Author: wyh
 * @Date: 2024-01-25 15:00
 * @Description: 定义一些dubbo相关的常量
                 注意：在实际使用中，客户端模块会被作为外部jar包引入到spring boot应用中。
 */
public interface DubboConstants {
    String DUBBO_PROTOCOL_PORT = "dubbo.protocol.port";

    String DUBBO_APPLICATION_NAME = "dubbo.application.name";

    String DUBBO_REGISTERY_ADDRESS = "dubbo.registery.address";

    int DUBBO_TIMEOUT = 5000;
}
