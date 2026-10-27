package com.mirrorsoul.mirrorsoul_api.billing;

import static org.assertj.core.api.Assertions.*;
import static com.mirrorsoul.mirrorsoul_api.billing.BillingTypes.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mock.env.MockEnvironment;

class BillingConfigurationTest {
    @Test void environmentRegistersTheExactStoreProductIdsAndSeconds() throws Exception {
        var env = new MockEnvironment()
                .withProperty("APPLE_TALK_TIME_PRODUCT_ID", "com.example.talk.30min")
                .withProperty("APPLE_TALK_TIME_PRODUCT_SECONDS", "1800")
                .withProperty("GOOGLE_TALK_TIME_PRODUCT_ID", "talk_30min")
                .withProperty("GOOGLE_TALK_TIME_PRODUCT_SECONDS", "1800")
                .withProperty("billing.apple-products[com.example.talk.2hours]", "7200")
                .withProperty("billing.google-products[talk_2hours]", "7200");
        new YamlPropertySourceLoader().load("app", new FileSystemResource("src/main/resources/application.yaml"))
                .forEach(source -> env.getPropertySources().addLast(source));
        var bound = Binder.get(env).bind("billing", Bindable.of(BillingProperties.class)).get();
        assertThat(bound.seconds(Store.APPLE, "com.example.talk.30min")).isEqualTo(1800);
        assertThat(bound.seconds(Store.GOOGLE, "talk_30min")).isEqualTo(1800);
        assertThat(bound.seconds(Store.APPLE, "com.example.talk.2hours")).isEqualTo(7200);
        assertThat(bound.seconds(Store.GOOGLE, "talk_2hours")).isEqualTo(7200);
        assertThatThrownBy(() -> bound.seconds(Store.APPLE, "other.product"))
                .isInstanceOf(com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException.class);
    }
}
