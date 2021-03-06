package org.talust.common.tools;

import org.talust.common.model.DepositAccount;

import java.util.*;
import java.util.concurrent.*;

/**
 * 缓存工具,内存缓存
 * 失效其的情况下,以秒进行计算
 */
public class CacheManager {
    private static CacheManager instance = new CacheManager();

    private  ScheduledExecutorService service = new ScheduledThreadPoolExecutor(1);
    private CacheManager() {
        service.scheduleAtFixedRate(() -> {//每10秒检测一下是否有超时的,有的则直接删除
            long nowTime = System.currentTimeMillis();
            List<String> needDel = new ArrayList<>();
            Iterator<Map.Entry<String, CacheData>> it = mapDt.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, CacheData> next = it.next();
                String key = next.getKey();
                CacheData value = next.getValue();
                if (value.getExpire() > 0 && value.getSaveTime() < nowTime) {
                    needDel.add(key);
                }
            }
            for (String s : needDel) {
                mapDt.remove(s);
            }

        }, 0, 10, TimeUnit.SECONDS);
    }

    public static CacheManager get() {
        return instance;
    }

    private Map<String, CacheData> mapDt = new ConcurrentHashMap<>();

    public <T> T get(String key) {
        CacheData<T> data = mapDt.get(key);
        if (data != null && (data.getExpire() <= 0 || data.getSaveTime() >= System.currentTimeMillis())) {
            return data.getData();
        }
        return null;
    }

    /**
     * 含有失效期的缓存,时间以秒为单位
     *
     * @param key
     * @param data
     * @param expire
     * @param <T>
     */
    public <T> void put(String key, T data, int expire) {
        mapDt.put(key, new CacheData(data, expire));
    }

    /**
     * 检验消息是否重复接收过,如果没有接收过,则加入缓存中,同时在timeOut中指明的时间点失效
     *
     * @param identifier
     * @param timeOut
     * @return
     */
    public boolean checkRepeat(String identifier, int timeOut) {
        CacheData cacheData = mapDt.get(identifier);
        if (cacheData != null) {//表明之前接收到过同样的消息
            if (cacheData.getExpire() > 0 && cacheData.getSaveTime() < System.currentTimeMillis()) {
                mapDt.remove(identifier);
                return false;
            }
            return true;
        }

        //将消息放于缓存中,当超过一定时间时,将会被消除掉
        put(identifier, null, timeOut);//不需要数据,只需要标识在即可
        return false;
    }

    /**
     * 不含有失效其的缓存
     *
     * @param key
     * @param data
     * @param <T>
     */
    public <T> void put(String key, T data) {
        mapDt.put(key, new CacheData(data, 0));
    }

    public void clear(String key) {
        mapDt.remove(key);
    }

    public void clearAll() {
        mapDt.clear();
    }

    //当前区块时间
    private int currentBlockTime;
    //当前区块高度
    private int currentBlockHeight = 0;
    //当前区块的hash值
    private byte[] currentBlockHash;
    //当前区块产生的ip地址
    private String currentBlockGenIp;

    private List<DepositAccount> deposits = new ArrayList<>();

    public int getCurrentBlockTime() {
        return currentBlockTime;
    }

    public void setCurrentBlockTime(int currentBlockTime) {
        this.currentBlockTime = currentBlockTime;
    }

    public int getCurrentBlockHeight() {
        return currentBlockHeight;
    }

    public void setCurrentBlockHeight(int currentBlockHeight) {
        this.currentBlockHeight = currentBlockHeight;
    }

    public byte[] getCurrentBlockHash() {
        return currentBlockHash;
    }

    public void setCurrentBlockHash(byte[] currentBlockHash) {
        this.currentBlockHash = currentBlockHash;
    }

    public String getCurrentBlockGenIp() {
        return currentBlockGenIp;
    }

    public void setCurrentBlockGenIp(String currentBlockGenIp) {
        this.currentBlockGenIp = currentBlockGenIp;
    }
}
