package io.digdag.core;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.Map;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.inject.Provider;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.util.Modules;
import io.digdag.commons.ThrowablesUtil;
import io.digdag.core.acroute.AccountRoutingModule;
import io.digdag.core.notification.NotificationModule;
import io.digdag.core.config.ConfigModule;
import io.digdag.core.log.LogModule;
import io.digdag.core.plugin.PluginSet;
import io.digdag.core.plugin.DynamicPluginModule;
import io.digdag.core.plugin.SystemPluginModule;
import io.digdag.metrics.StdDigdagMetrics;
import io.digdag.spi.metrics.DigdagMetrics;
import org.embulk.guice.LifeCycleInjector;
import com.fasterxml.jackson.module.guice.ObjectMapperModule;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import io.digdag.client.config.Config;
import io.digdag.client.config.ConfigElement;
import io.digdag.client.config.ConfigFactory;
import io.digdag.client.api.JacksonTimeModule;

public class DigdagEmbed
        implements AutoCloseable
{
    public static class Bootstrap
    {
        private final List<Function<? super List<Module>, ? extends Iterable<? extends Module>>> moduleOverrides = new ArrayList<>();
        private ConfigElement systemConfig = ConfigElement.empty();
        private PluginSet systemPlugins = PluginSet.empty();
        private Map<String, String> environment = ImmutableMap.of();

        public Bootstrap addModules(Module... additionalModules)
        {
            return addModules(Arrays.asList(additionalModules));
        }

        public Bootstrap addModules(Iterable<? extends Module> additionalModules)
        {
            final List<Module> copy = ImmutableList.copyOf(additionalModules);
            return overrideModules(modules -> Iterables.concat(modules, copy));
        }

        public Bootstrap overrideModules(Function<? super List<Module>, ? extends Iterable<? extends Module>> function)
        {
            moduleOverrides.add(function);
            return this;
        }

        public Bootstrap overrideModulesWith(Module... overridingModules)
        {
            return overrideModulesWith(Arrays.asList(overridingModules));
        }

        public Bootstrap overrideModulesWith(Iterable<? extends Module> overridingModules)
        {
            return overrideModules(modules -> ImmutableList.of(Modules.override(modules).with(overridingModules)));
        }

        public Bootstrap setEnvironment(Map<String, String> environment)
        {
            this.environment = environment;
            return this;
        }

        public Bootstrap setSystemConfig(ConfigElement systemConfig)
        {
            this.systemConfig = systemConfig;
            return this;
        }

        public Bootstrap setSystemPlugins(PluginSet systemPlugins)
        {
            this.systemPlugins = systemPlugins;
            return this;
        }

        public DigdagEmbed initialize()
        {
            return build(true);
        }

        public DigdagEmbed initializeWithoutShutdownHook()
        {
            return build(false);
        }

        private DigdagEmbed build(boolean destroyOnShutdownHook)
        {
            org.embulk.guice.Bootstrap bootstrap = build();

            LifeCycleInjector injector;
            if (destroyOnShutdownHook) {
                injector = bootstrap.initialize();
            }
            else {
                injector = bootstrap.initializeCloseable();
            }

            return new DigdagEmbed(injector);
        }

        public org.embulk.guice.Bootstrap build()
        {
            org.embulk.guice.Bootstrap bootstrap = new org.embulk.guice.Bootstrap()
                .requireExplicitBindings(true)
                .addModules(standardModules(systemConfig));
            moduleOverrides.stream().forEach(override -> bootstrap.overrideModules(override));
            return bootstrap;
        }

        private List<Module> standardModules(ConfigElement systemConfig)
        {
            ImmutableList.Builder<Module> builder = ImmutableList.builder();
            builder.addAll(Arrays.asList(
                    new ObjectMapperModule()
                        .registerModule(new GuavaModule())
                        .registerModule(new JacksonTimeModule()),
                    new DynamicPluginModule(),
                    new SystemPluginModule(systemPlugins),
                    new LogModule(),
                    new ConfigModule(),
                    new NotificationModule(),
                    new EnvironmentModule(environment),
                    new AccountRoutingModule(),
                    (binder) -> {
                        binder.bind(ConfigElement.class).toInstance(systemConfig);
                        binder.bind(Config.class).toProvider(SystemConfigProvider.class);
                        binder.bind(TempFileManager.class).toProvider(TempFileManagerProvider.class).in(Scopes.SINGLETON);
                        binder.bind(DigdagMetrics.class).toInstance(StdDigdagMetrics.empty());
                        binder.bind(Limits.class).asEagerSingleton();
                    }
                ));
            return builder.build();
        }
    }

    public static class SystemConfigProvider
            implements Provider<Config>
    {
        private Config systemConfig;

        @Inject
        public SystemConfigProvider(ConfigElement ce, ConfigFactory cf)
        {
            this.systemConfig = ce.toConfig(cf);
        }

        @Override
        public Config get()
        {
            return systemConfig;
        }
    }

    public static class TempFileManagerProvider
            implements Provider<TempFileManager>
    {
        private final TempFileManager manager;

        @Inject
        public TempFileManagerProvider(Config systemConfig)
        {
            Path dir = systemConfig.getOptional("tmpdir", String.class)
                    .transform(s -> Paths.get(s))
                    .or(() -> {
                        try {
                            return Files.createTempDirectory("digdag-tempdir");
                        }
                        catch (IOException ex) {
                            throw ThrowablesUtil.propagate(ex);
                        }
                    });
            this.manager = new TempFileManager(dir);
        }

        @Override
        public TempFileManager get()
        {
            return manager;
        }
    }

    private final LifeCycleInjector injector;

    DigdagEmbed(LifeCycleInjector injector)
    {
        this.injector = injector;
    }

    public Injector getInjector()
    {
        return injector;
    }

    @Override
    public void close() throws Exception
    {
        injector.destroy();
    }
}
