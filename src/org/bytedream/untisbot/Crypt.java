package org.bytedream.untisbot;

import javax.crypto.*;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * Class to en- / decrypt strings {@see https://github.com/ByteDream/cryptoGX}
 *
 * @version 1.0
 * @since 1.0
 */
public class Crypt {

    private final String key;

    private final String secretKeyFactoryAlgorithm = "PBKDF2WithHmacSHA512";
    private final int keySize = 256;
    private final int iterations = 65536;

    public Crypt(String key) {
        this.key = key;
    }

    /**
     * Generates a new secret key for en- / decryption
     *
     * @return the secret key
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeySpecException
     * @since 1.0
     */
    private byte[] createSecretKey() throws NoSuchAlgorithmException, InvalidKeySpecException {
        SecretKeyFactory factory = SecretKeyFactory.getInstance(secretKeyFactoryAlgorithm);
        PBEKeySpec keySpec = new PBEKeySpec(key.toCharArray(), new byte[16], iterations, keySize);

        return factory.generateSecret(keySpec).getEncoded();
    }

    /**
     * Encrypts a given string
     *
     * @param string string to encrypt
     * @return the encrypted string
     * @throws BadPaddingException
     * @throws NoSuchAlgorithmException
     * @throws IllegalBlockSizeException
     * @throws NoSuchPaddingException
     * @throws InvalidKeyException
     * @throws InvalidKeySpecException
     * @since 1.0
     */
    public String encrypt(String string) throws BadPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, NoSuchPaddingException, InvalidKeyException, InvalidKeySpecException {
        Key secretKey = new SecretKeySpec(createSecretKey(), "AES");

        Cipher encryptCipher = Cipher.getInstance("AES");
        encryptCipher.init(Cipher.ENCRYPT_MODE, secretKey);
        return Base64.getEncoder().encodeToString(encryptCipher.doFinal(string.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Decrypts a given string
     *
     * @param string string to decrypt
     * @return the decypted string
     * @throws BadPaddingException
     * @throws IllegalBlockSizeException
     * @throws NoSuchPaddingException
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeySpecException
     * @throws InvalidKeyException
     */
    public String decrypt(String string) throws BadPaddingException, IllegalBlockSizeException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException {
        Key secretKey = new SecretKeySpec(createSecretKey(), "AES");

        Cipher decryptCipher = Cipher.getInstance("AES");
        decryptCipher.init(Cipher.DECRYPT_MODE, secretKey);
        return new String(decryptCipher.doFinal(Base64.getDecoder().decode(string)), StandardCharsets.UTF_8);
    }

}
