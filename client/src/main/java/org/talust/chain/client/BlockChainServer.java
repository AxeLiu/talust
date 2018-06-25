package org.talust.chain.client;

import lombok.extern.slf4j.Slf4j;
import org.talust.chain.account.MiningAddress;
import org.talust.chain.block.SynBlock;
import org.talust.chain.block.model.Block;
import org.talust.chain.client.handler.*;
import org.talust.chain.client.validator.BlockArrivedValidator;
import org.talust.chain.client.validator.NodeExitValidator;
import org.talust.chain.client.validator.NodeJoinValidator;
import org.talust.chain.client.validator.TransactionValidator;
import org.talust.chain.common.model.MessageType;
import org.talust.chain.common.tools.CacheManager;
import org.talust.chain.common.tools.Constant;
import org.talust.chain.common.tools.SerializationUtil;
import org.talust.chain.consensus.ConsensusService;
import org.talust.chain.consensus.handler.MasterReqHandler;
import org.talust.chain.consensus.handler.MasterRespHandler;
import org.talust.chain.consensus.handler.NewMasterReqHandler;
import org.talust.chain.consensus.handler.NewMasterRespHandler;
import org.talust.chain.network.MessageHandler;
import org.talust.chain.network.MessageValidator;
import org.talust.chain.network.NodeConsole;
import org.talust.chain.network.model.MyChannel;
import org.talust.chain.network.netty.ChannelContain;
import org.talust.chain.network.netty.queue.MessageQueueHolder;
import org.talust.chain.storage.AccountStorage;
import org.talust.chain.storage.BlockStorage;
import org.talust.chain.storage.ChainStateStorage;
import org.talust.chain.storage.TransactionStorage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Slf4j
public class BlockChainServer {
    private BlockStorage blockStorage = BlockStorage.get();
    private AccountStorage accountStorage = AccountStorage.get();
    private ChainStateStorage chainStateStorage = ChainStateStorage.get();
    private TransactionStorage transactionStorage = TransactionStorage.get();
    private MessageQueueHolder queueHolder = MessageQueueHolder.get();

    private static BlockChainServer instance = new BlockChainServer();

    private BlockChainServer() {
    }

    public static BlockChainServer get() {
        return instance;
    }

    public void start() throws Exception {
        log.info("初始化消息处理器...");
        initValidators();
        initHandlers();
        MessageQueueHolder.get().start();

        NodeConsole nc = new NodeConsole();
        nc.start();//启动网络的收发

        Collection<MyChannel> allChannel = ChannelContain.get().getMyChannels();
        log.info("当前节点所连接的节点数:{}", allChannel.size());
        if (allChannel != null && allChannel.size() > 0) {//说明有其他的节点
            SynBlock.get().setBlockArrivedValidator(new BlockArrivedValidator());
            SynBlock.get().setBlockArrivedHandler(new BlockArrivedHandler());
            SynBlock.get().startSynBlock();//同步区块
        }

        //启动共识
        ConsensusService.get().start();

    }

    private void initValidators() {
        addValidator(MessageType.BLOCK_ARRIVED, new BlockArrivedValidator());
        addValidator(MessageType.NODE_EXIT, new NodeExitValidator());
        addValidator(MessageType.NODE_JOIN, new NodeJoinValidator());
        addValidator(MessageType.TRANSACTION, new TransactionValidator());
    }

    /**
     * 初始化消息处理器
     */
    private void initHandlers() {
        addHandler(MessageType.NODES_REQ, new NetNodesReqHandler());
        addHandler(MessageType.NODES_RESP, new NetNodesRespHandler());
        addHandler(MessageType.BLOCK_ARRIVED, new BlockArrivedBroadHandler());
        addHandler(MessageType.BLOCK_ARRIVED, new BlockArrivedHandler());
        addHandler(MessageType.HEIGHT_REQ, new BlockHeightReqHandler());
        addHandler(MessageType.HEIGHT_RESP, new BlockHeightRespHandler());
        addHandler(MessageType.BLOCK_REQ, new BlockDataReqHandler());
        addHandler(MessageType.BLOCK_RESP, new BlockDataRespHandler());
        addHandler(MessageType.ERROR_MESSAGE, new ErrorMessageHandler());
        addHandler(MessageType.NODE_JOIN, new NodeJoinHandler());
        addHandler(MessageType.NODE_EXIT, new NodeExitHandler());
        addHandler(MessageType.TRANSACTION, new TransactionHandler());
        addHandler(MessageType.MASTER_REQ, new MasterReqHandler());
        addHandler(MessageType.MASTER_RESP, new MasterRespHandler());
        addHandler(MessageType.NEW_MASTER_REQ, new NewMasterReqHandler());
        addHandler(MessageType.NEW_MASTER_RESP, new NewMasterRespHandler());
    }

    public void addValidator(MessageType messageType, MessageValidator validator) {
        queueHolder.setValidator(messageType, validator);
    }

    public void addHandler(MessageType messageType, MessageHandler handler) {
        queueHolder.addHandler(messageType, handler);
    }


    /**
     * 初始化存储
     */
    public void initStorage() throws Exception {
        log.info("初始化存储...");
        blockStorage.init();
        chainStateStorage.init();
        transactionStorage.init();
        accountStorage.init();

        log.info("初始化缓存...");
        byte[] nowBlockHash = blockStorage.get(Constant.NOW_BLOCK_HASH);

        if (nowBlockHash != null) {
            byte[] nowBlock = blockStorage.get(nowBlockHash);
            byte[] addrBytes = blockStorage.get(Constant.MINING_ADDRESS);
            List<String>  miningAddress = new ArrayList<>();
            if(addrBytes!=null){
                MiningAddress miningAddres = SerializationUtil.deserializer(addrBytes, MiningAddress.class);
                if (miningAddres != null) {
                    miningAddress = miningAddres.getAddress();
                }
                CacheManager.get().put(new String(Constant.MINING_ADDRESS), miningAddress);
            }
            if (nowBlock != null) {
                Block block = SerializationUtil.deserializer(nowBlock, Block.class);
                //将区块的最新打包时间写入缓存
                CacheManager.get().setCurrentBlockTime(block.getHead().getTime());
                CacheManager.get().setCurrentBlockHeight(block.getHead().getHeight());
                CacheManager.get().setCurrentBlockHash(nowBlockHash);
            }
        }

    }
}
