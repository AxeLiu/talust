package org.talust.common.crypto;

import org.spongycastle.crypto.BufferedBlockCipher;
import org.spongycastle.crypto.InvalidCipherTextException;
import org.spongycastle.crypto.engines.AESFastEngine;
import org.spongycastle.crypto.modes.CBCBlockCipher;
import org.spongycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.spongycastle.crypto.params.KeyParameter;
import org.spongycastle.crypto.params.ParametersWithIV;

import java.security.SecureRandom;
import java.util.Arrays;

/**
 * AES加密
 */
public class AESEncrypt {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public static byte[] encrypt(byte[] plainBytes, String password) throws InvalidCipherTextException {
        EncryptedData ed = encrypt(plainBytes, new KeyParameter(Sha256Hash.hash(password.getBytes())));
        return ed.getEncryptedBytes();
    }

    /**
     * 加密
     *
     * @param plainBytes
     * @param aesKey
     * @return EncryptedData
     */
    public static EncryptedData encrypt(byte[] plainBytes, KeyParameter aesKey) throws InvalidCipherTextException {
        return encrypt(plainBytes, EncryptedData.DEFAULT_IV, aesKey);
    }

    /**
     * 加密
     *
     * @param plainBytes
     * @param iv
     * @param aesKey
     * @return EncryptedData
     */
    public static EncryptedData encrypt(byte[] plainBytes, byte[] iv, KeyParameter aesKey) throws InvalidCipherTextException {
        Utils.checkNotNull(plainBytes);
        Utils.checkNotNull(aesKey);

        if (iv == null) {
            iv = new byte[16];
            SECURE_RANDOM.nextBytes(iv);
        }

        ParametersWithIV keyWithIv = new ParametersWithIV(aesKey, iv);

        // Encrypt using AES.
        BufferedBlockCipher cipher = new PaddedBufferedBlockCipher(new CBCBlockCipher(new AESFastEngine()));
        cipher.init(true, keyWithIv);
        byte[] encryptedBytes = new byte[cipher.getOutputSize(plainBytes.length)];
        final int length1 = cipher.processBytes(plainBytes, 0, plainBytes.length, encryptedBytes, 0);
        final int length2 = cipher.doFinal(encryptedBytes, length1);

        return new EncryptedData(iv, Arrays.copyOf(encryptedBytes, length1 + length2));
    }

    public static byte[] decrypt(byte[] dataToDecrypt,String password) throws InvalidCipherTextException {
        EncryptedData data = new EncryptedData(EncryptedData.DEFAULT_IV,dataToDecrypt);
        return decrypt(data,new KeyParameter(Sha256Hash.hash(password.getBytes())));
    }

    /**
     * 解密
     *
     * @param dataToDecrypt
     * @param aesKey
     * @return byte[]
     */
    public static byte[] decrypt(EncryptedData dataToDecrypt, KeyParameter aesKey) throws InvalidCipherTextException {
        Utils.checkNotNull(dataToDecrypt);
        Utils.checkNotNull(aesKey);

        ParametersWithIV keyWithIv = new ParametersWithIV(new KeyParameter(aesKey.getKey()), dataToDecrypt.getInitialisationVector());

        // Decrypt the message.
        BufferedBlockCipher cipher = new PaddedBufferedBlockCipher(new CBCBlockCipher(new AESFastEngine()));
        cipher.init(false, keyWithIv);

        byte[] cipherBytes = dataToDecrypt.getEncryptedBytes();
        byte[] decryptedBytes = new byte[cipher.getOutputSize(cipherBytes.length)];
        final int length1 = cipher.processBytes(cipherBytes, 0, cipherBytes.length, decryptedBytes, 0);
        final int length2 = cipher.doFinal(decryptedBytes, length1);

        return Arrays.copyOf(decryptedBytes, length1 + length2);
    }

    public static void main(String[] args) throws Exception{
        String str = "aww测试";
        String pw = "sssssfds";
        byte[] encrypt = encrypt(str.getBytes(), pw);
        byte[] decrypt = decrypt(encrypt, pw);
        System.out.println(new String(decrypt));

//        EncryptedData data = encrypt(str.getBytes(), new KeyParameter(Sha256Hash.hash(pw.getBytes())));
//        Log.debug(data.toString());
//
//        Log.debug(new String(decrypt(data, new KeyParameter(Sha256Hash.hash(pw.getBytes())))));
//
//        byte[] encrypt = encrypt(str.getBytes(), pw);
//        System.out.println(encrypt);



    }
}
