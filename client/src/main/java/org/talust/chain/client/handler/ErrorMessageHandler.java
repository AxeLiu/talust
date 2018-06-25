package org.talust.chain.client.handler;

import lombok.extern.slf4j.Slf4j;
import org.talust.chain.common.model.MessageChannel;
import org.talust.chain.network.MessageHandler;
import org.talust.chain.network.netty.SynRequest;

@Slf4j//远端返回的错误消息的处理,目前暂未考虑细分哪些错误消息,而是通过消息的内容说明自行处理
public class ErrorMessageHandler implements MessageHandler {
    @Override
    public boolean handle(MessageChannel message) {
        log.info("远端ip:{} 返回了错误...", message.getFromIp());
        SynRequest.get().synResp(message);
        return true;
    }
}
