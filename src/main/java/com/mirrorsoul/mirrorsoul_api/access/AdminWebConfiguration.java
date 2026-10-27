package com.mirrorsoul.mirrorsoul_api.access;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
@Configuration @RequiredArgsConstructor
public class AdminWebConfiguration implements WebMvcConfigurer {
 private final org.springframework.beans.factory.ObjectProvider<AdminAccessInterceptor> access;
 @Override public void addInterceptors(InterceptorRegistry registry) {var interceptor=access.getIfAvailable();if(interceptor!=null) registry.addInterceptor(interceptor).addPathPatterns("/admin/**");}
}
