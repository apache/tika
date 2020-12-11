package org.apache.tika.server.core.config;

import org.apache.commons.codec.binary.Base64;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.server.core.ParseContextConfig;

import javax.ws.rs.core.MultivaluedMap;

import static java.nio.charset.StandardCharsets.UTF_8;

public class PasswordProviderConfig implements ParseContextConfig {
    private static final Base64 BASE_64 = new Base64();

    public static final String PASSWORD = "Password";
    public static final String PASSWORD_BASE64_UTF8 = "Password_Base64_UTF-8";

    @Override
    public void configure(MultivaluedMap<String, String> httpHeaders,
                          Metadata metadata, ParseContext context) {
        String tmpPassword = httpHeaders.getFirst(PASSWORD_BASE64_UTF8);
        if (tmpPassword != null) {
            tmpPassword = decodeBase64UTF8(tmpPassword);
        } else {
            tmpPassword = httpHeaders.getFirst(PASSWORD);
        }
        if (tmpPassword != null) {
            final String password = tmpPassword;
            context.set(PasswordProvider.class, new PasswordProvider() {
                @Override
                public String getPassword(Metadata metadata) {
                    return password;
                }
            });
        }
    }

    private static String decodeBase64UTF8(String s) {
        byte[] bytes = BASE_64.decode(s);
        return new String(bytes, UTF_8);
    }

}
