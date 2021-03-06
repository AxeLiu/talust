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

package org.talust.network.netty;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.CollectionCodec;
import com.sun.scenario.effect.Color4f;
import io.netty.channel.Channel;
import io.netty.util.internal.ConcurrentSet;
import lombok.extern.slf4j.Slf4j;
import org.talust.account.Account;
import org.talust.common.crypto.Utils;
import org.talust.common.model.Message;
import org.talust.common.model.MessageChannel;
import org.talust.common.model.MessageType;
import org.talust.common.model.SuperNode;
import org.talust.common.tools.*;
import org.talust.network.model.AllNodes;
import org.talust.network.model.MyChannel;
import org.talust.network.netty.client.NodeClient;
import org.talust.network.netty.queue.MessageQueue;
import org.talust.storage.AccountStorage;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 连接管理者,用于实现底层的连接,上层应用中不需要关心本层次的连接问题
 *
 * @author
 */

@Slf4j
public class ConnectionManager {
    private static ConnectionManager instance = new ConnectionManager();

    private ConnectionManager() {
    }

    public static ConnectionManager get() {
        return instance;
    }

    /**
     * 存储当前网络的超级节点ip地址
     */
    private Set<String> superIps = new ConcurrentSet<>();
    /**
     * 超级节点信息
     */
    private Map<String, SuperNode> superNodes = new HashMap<>();
    /**
     * 当前节点是否是超级节点
     */
    public boolean superNode = false;
    /**
     * 当前节点是否是创世ip
     */
    public boolean genesisIp = false;

    /**
     * 节点自身ip地址
     */
    public String selfIp = null;

    private MessageQueue mq = MessageQueue.get();
    /**
     * 存储当前节点的ip地址,可能有多个
     */
    private Set<String> myIps = new HashSet<>();


    /**
     * 初始化方法,主要用于定时检测节点连接情况,发现连接数过少时,就需要同步一下连接
     */
    public void init() {
        initSuperIps();
        if (!superNode) {
            normalNodeJoin();
        } else {
            superNodeJoin();
        }
    }
    /**
     *
     */



    /**
     * 连接普通节点
     */
    private void normalNodeJoin() {
        ChannelContain cc = ChannelContain.get();
        JSONObject peerJ = JSONObject.parseObject(PeersManager.get().peerCont);
        if (peerJ.entrySet().size() == 0) {
            for (String fixedIp : superIps) {
                if(nodesJoinBroadcast(fixedIp)){
                    try {
                        peerJ = getPeersOnline(fixedIp,peerJ);
                        break;
                    } catch (Exception e) {
                        continue;
                    }
                }
            }
        }
        List<String> unusedIps = new ArrayList<>();
        for (Object map : peerJ.entrySet()) {
            String trust = (String) ((Map.Entry) map).getValue();
            String peerIp = (String) ((Map.Entry) map).getKey();
            //TODO 需要优化连接请求次数，减少网络消费。
            //TODO 否则业务节点需要请求是否可以连接，而后断开，再进行连接，而后再进行文件获取
            //TODO 主节点也是先被请求是否可以连接，而后断开，再去获取peers文件，然后断开。如果不得已而去连接的话，则会再次请求是否可以连接，然后断开，再去连接
            if(!ChannelContain.get().validateIpIsConnected(peerIp)){
                if (!"0".equals(trust)) {
                    if(nodesJoinBroadcast(peerIp)){
                        String status = connectByIp(peerIp, cc);
                        if ("FULL".equals(status)) {
                            break;
                        }
                        switch (status) {
                            case "OK":
                                try {
                                    getPeersOnline(peerIp);
                                } catch (Exception e) {
                                    continue;
                                }
                                break;
                            case "FAIL":
                                unusedIps.add((String) ((Map.Entry) map).getKey());
                                break;
                            default:
                                break;
                        }
                    }else{
                        unusedIps.add((String) ((Map.Entry) map).getKey());
                    }
                }
            }
        }
        PeersManager.get().removePeerList(unusedIps);
        if (cc.getActiveConnectionCount() == 0) {
            superNodeJoin();
        }
    }

    /**
     * 链接单个节点
     */
    private String connectByIp(String ip, ChannelContain cc) {
        try {
            int nowConnSize = cc.getActiveConnectionCount();
            if (nowConnSize < Configure.MAX_ACTIVE_CONNECT_COUNT) {
                log.info("我允许的主动连接总数:{},当前主动连接总数:{},连接我的总数:{},准备连接的ip:{}",
                        Configure.MAX_ACTIVE_CONNECT_COUNT, cc.getActiveConnectionCount(), cc.getPassiveConnCount(), ip);
                if (!ChannelContain.get().validateIpIsConnected(ip)) {
                    log.info("本节点连接目标ip地址:{}", ip);
                    NodeClient nodeClient = new NodeClient();
                    Channel connect = nodeClient.connect(ip, Constant.PORT);
                    cc.addChannel(connect, false);
                }
                return "OK";
            } else {
                return "FULL";
            }
        } catch (Throwable e) {
            return "FAIL";
        }
    }


    /**
     * 获取连接节点的已连接peers 数据
     */
    public JSONObject getPeersOnline(String peersIp) throws Exception {
        Channel channel;
        boolean isConnected =ChannelContain.get().validateIpIsConnected(peersIp);
        if(!isConnected){
            NodeClient nc = new NodeClient();
            channel = nc.connect(peersIp, Constant.PORT);
            ChannelContain.get().addChannel(channel, false);
        }else{
            channel=  ChannelContain.get().getChannelByIp(peersIp);
        }
        JSONObject peers = new JSONObject();
        Message nm = new Message();
        nm.setType(MessageType.NODES_REQ.getType());
        log.info("向节点ip:{} 请求当前网络的所有节点...", peersIp);
        InetSocketAddress inetSocketAddress = (InetSocketAddress) channel.remoteAddress();
        String remoteIp = inetSocketAddress.getAddress().getHostAddress();
        MessageChannel message = SynRequest.get().synReq(nm, remoteIp);
        if (message != null) {
            peers = SerializationUtil.deserializer(message.getMessage().getContent(), JSONObject.class);
            if (peers != null && peers.keySet().size() > 0) {
                log.info("节点ip:{} 返回当前网络的所有节点数:{}", peersIp, peers.keySet().size());
            }
            if(peers.containsKey(selfIp)){
                peers.remove(selfIp);
            }
            PeersManager.get().addPeer(peers);
        }
        if(!isConnected){
            ChannelContain.get().removeChannel(channel);
        }
        return peers;
    }
    public JSONObject getPeersOnline(String peersIp,JSONObject peerNow) throws Exception {
        peerNow.putAll(getPeersOnline(peersIp));
        return peerNow;
    }


    /**
     * 连接到超级节点
     */
    private void superNodeJoin() {
        List<String> snodes = new ArrayList<>(superIps.size());
        for (String fixedIp : superIps) {
            snodes.add(fixedIp);
        }
        log.info("连接到网络,当前节点ip:{},全网超级节点数:{}", this.selfIp, snodes.size());
        ChannelContain cc = ChannelContain.get();
        int size = snodes.size();
        if (size > 0) {
            Random rand = new Random();
            while (size > 0) {
                //确保能够成功连接到一台节点获得当前所有连接
                int selNode = rand.nextInt(size);
                //随机选择一台固定节点以获取当前所有可用的网络节点
                String node = snodes.get(selNode);
                try {
                    getPeersOnline(node);
                    break;
                } catch (Exception e) {
                }
                snodes.remove(selNode);
                size = snodes.size();
            }
            if(snodes.size()>0){
                connectAllSuperNode(superIps, cc);
            }
        }
    }

    /**
     * 对象节点通知本节点加入
     */
    public boolean nodesJoinBroadcast(String ip) {
        Channel channel = null;
        try {
            NodeClient nc = new NodeClient();
             channel = nc.connect(ip, Constant.PORT);
            ChannelContain.get().addChannel(channel, false);
            Message message = new Message();
            message.setType(MessageType.NODE_JOIN.getType());
            message.setContent(selfIp.getBytes());
            log.info("向地址{}发送当前节点ip:{}的连接请求",ip, selfIp);
            MessageChannel  messageChannel = SynRequest.get().synReq(message, ip);
            if (messageChannel != null) {
                String  nodeJoinResp = SerializationUtil.deserializer(messageChannel.getMessage().getContent(), String.class);
                log.info("连接节点ip:{} 返回连接结果为:{}", ip, nodeJoinResp);
                if (Boolean.parseBoolean(nodeJoinResp)) {
                    return true;
                }
            }else{
                log.info("连接节点ip:{}，请求失败", ip);
            }
        } catch (Exception e) {
            log.info("连接节点ip:{}，请求失败", ip);
            return false;
        }finally {
            if(channel!=null){
                ChannelContain.get().removeChannel(channel);
            }
        }
        return false;
    }


    /**
     * 连接所有超级节点
     *
     * @param nodeIps
     * @param cc
     */
    private void connectAllSuperNode(Collection<String> nodeIps, ChannelContain cc) {
        int needConCount = 0;
        if(isSuperNode()){
            needConCount =  Configure.MAX_SUPER_ACTIVE_CONNECT_COUNT-cc.getActiveConnectionCount();
        }else{
            needConCount = Configure.MAX_ACTIVE_CONNECT_COUNT-cc.getActiveConnectionCount();
        }
        for (String ip : nodeIps) {
            if (needConCount > 0) {
                try {
                    if (!ChannelContain.get().validateIpIsConnected(ip)) {
                        if (nodesJoinBroadcast(ip)) {
                            log.info("本节点连接超级节点目标ip地址:{}", ip);
                            NodeClient tmpnc = new NodeClient();
                            Channel connect = tmpnc.connect(ip, Constant.PORT);
                            cc.addChannel(connect, false);
                            InetSocketAddress insocket = (InetSocketAddress) connect.localAddress();
                            selfIp = insocket.getAddress().getHostAddress();
                        }
                    }
                } catch (Throwable e) {
                }
                needConCount--;
            } else {
                break;
            }
        }
    }


    /**
     * 初始化固定超级服务器ip地址,用于当前节点的连接所用
     */
    private void initSuperIps() {
        try {
            Enumeration<?> e1 = NetworkInterface.getNetworkInterfaces();
            while (e1.hasMoreElements()) {
                NetworkInterface ni = (NetworkInterface) e1.nextElement();
                Enumeration<?> e2 = ni.getInetAddresses();
                while (e2.hasMoreElements()) {
                    InetAddress ia = (InetAddress) e2.nextElement();
                    if (ia instanceof Inet6Address) {
                        continue;
                    }
                    myIps.add(ia.getHostAddress());
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }

        JSONObject gip = getJsonFile(Configure.GENESIS_SERVER_ADDR);
        JSONObject root = gip.getJSONObject("root");
        CacheManager.get().put("ROOT_PK", root.getString("publickey"));
        CacheManager.get().put("ROOT_SIGN", root.getString("sign"));
        JSONObject talust = gip.getJSONObject("talust");
        CacheManager.get().put("TALUST_PK", talust.getString("publickey"));
        CacheManager.get().put("TALUST_SIGN", talust.getString("sign"));
        if (myIps.contains(gip.getString("genesisIp"))) {
            genesisIp = true;
        }
        JSONObject ips = getJsonFile(Configure.NODE_SERVER_ADDR);
        List<String> minings = new ArrayList<>();
        for (Object map : ips.entrySet()) {
            JSONObject ipContent = (JSONObject) ((Map.Entry) map).getValue();
            String ip = ipContent.getString("ip");
            minings.add(ipContent.getString("address"));
            SuperNode snode = new SuperNode();
            snode.setCode(Integer.parseInt((String) ((Map.Entry) map).getKey()));
            snode.setIp(ip);
            snode.setAddress(Utils.showAddress(Utils.getAddress(ipContent.getBytes("address"))));
            if (!myIps.contains(ip)) {
                superIps.add(ip);
            } else {
                superNode = true;
                AccountStorage.get().superNodeLogin();
            }
            superNodes.put(ip, snode);
        }
        CacheManager.get().put(new String(Constant.MINING_ADDRESS), minings);
        if (null == selfIp) {
            selfIp = myIps.iterator().next();
        }
        log.info("获得超级节点数为:{}", superIps.size());
    }

    public JSONObject getJsonFile(String filePath) {
        JSONObject jsonObject = null;
        try {
            String input = getIps(filePath);
            jsonObject = JSONObject.parseObject(input);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return jsonObject;
    }

    /**
     * 获取超级服务器节点信息
     *
     * @param filePath
     * @return
     */
    private String getIps(String filePath) {
        int HttpResult;
        String ee = new String();
        try {
            URL url = new URL(filePath);
            URLConnection urlconn = url.openConnection();
            urlconn.connect();
            HttpURLConnection httpconn = (HttpURLConnection) urlconn;
            HttpResult = httpconn.getResponseCode();
            if (HttpResult != HttpURLConnection.HTTP_OK) {
                log.error("无法连接到服务器获取节点列表...");
            } else {
                InputStreamReader isReader = new InputStreamReader(urlconn.getInputStream());
                BufferedReader reader = new BufferedReader(isReader);
                StringBuffer buffer = new StringBuffer();
                String line;
                line = reader.readLine();
                while (line != null) {
                    buffer.append(line);
                    line = reader.readLine();
                }
                ee = buffer.toString();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ee;
    }

    /**
     * 获取超级网络节点
     */
    public Set<String> getSuperIps() {
        return superIps;
    }

    /**
     * 判断ip是否是本节点的ip
     */
    public boolean isSelfIp(String ip) {
        return myIps.contains(ip);
    }

    /**
     * 判断当前ip是否是超级节点
     */
    public boolean isSuperNode() {
        for (String myIp : myIps) {
            if (superIps.contains(myIp)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 返回当前节点的ip地址,即对外的ip地址
     */
    public String getSelfIp() {
        return this.selfIp;
    }

    public Collection<SuperNode> getSuperNodes() {
        return this.superNodes.values();
    }

    /**
     * 获取超级节点的信息
     */
    public SuperNode getSuperNodeByIp(String superIp) {
        return superNodes.get(superIp);
    }

}
