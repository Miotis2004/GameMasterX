package com.gamemasterx.server.config;

import com.gamemasterx.server.filter.AuthFilter;
import com.gamemasterx.server.filter.CorrelationIdFilter;
import com.gamemasterx.server.filter.CsrfFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        FilterRegistrationBean<CorrelationIdFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new CorrelationIdFilter());
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(1);
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<AuthFilter> authFilter() {
        FilterRegistrationBean<AuthFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new AuthFilter());
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(2);
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<CsrfFilter> csrfFilter() {
        FilterRegistrationBean<CsrfFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new CsrfFilter());
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(3);
        return registrationBean;
    }
}
