package io.digdag.core.crypto;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Provider;
import io.digdag.client.config.Config;
import io.digdag.core.database.DisabledSecretCrypto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecretCryptoProvider
        implements Provider<SecretCrypto>
{
    private static final Logger logger = LoggerFactory.getLogger(SecretCryptoProvider.class);

    private final SecretCrypto crypto;

    @Inject
    public SecretCryptoProvider(Config systemConfig)
    {
        this.crypto = new DisabledSecretCrypto();
        logger.info("secret encryption engine: {}", this.crypto.getName());
    }

    @Override
    public SecretCrypto get()
    {
        return crypto;
    }
}
