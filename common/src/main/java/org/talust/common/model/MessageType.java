/*
 * MIT License
 *
 * Copyright (c) 2017-2018 talust.org talust.io
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package org.talust.common.model;

public enum MessageType {
    HEARTBEAT_REQ(1), //心跳请求
    HEARTBEAT_RESP(2),//心跳响应
    NODES_REQ(3), //获取节点请求
    NODES_RESP(4),//获取节点响应
    BLOCK_ARRIVED(5),//块数据到来
    HEIGHT_REQ(7),//区块高度请求
    HEIGHT_RESP(8),//区块高度响应
    BLOCK_REQ(9),//获取区块请求
    BLOCK_RESP(10),//获取区块响应
    NODE_JOIN(11),//节点申请加入
    NODE_JOIN_RESP(21),//节点加入结果
    NODE_EXIT(12),//节点退出
    ERROR_MESSAGE(13),//错误消息处理,一般针对请求响应模型才会有,遇到此种情况,直接向异步请求端投递即可
    TRANSACTION(14),//接收到交易数据
    MASTER_REQ(17),//master请求
    MASTER_RESP(18),//master响应
    NEW_MASTER_REQ(19),//切换新的master请求d
    NEW_MASTER_RESP(20),//切换新的master响应
    END(10000)//结束,主要是为了开发时新增消息类型方便/
    ;


    private int type;

    /**
     * @param type
     */
    MessageType(int type) {
        this.type = type;
    }

    public int getType() {
        return type;
    }

    public static MessageType getMessageType(int type) {
        for (MessageType b : MessageType.values()) {
            if (b.getType() == type) {
                return b;
            }
        }
        return null;
    }

}
