package com.example.clinic.config;

import com.example.clinic.controller.paging.PageQueryArgumentResolver;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Adds clinic-specific argument binding to Spring MVC.
 *
 * <p>Implementing {@code WebMvcConfigurer} contributes the paging resolver while leaving Boot's MVC
 * configuration in place. The configuration uses no inter-bean method calls.
 */
@Configuration(proxyBeanMethods = false)
public class WebConfiguration implements WebMvcConfigurer {

    /**
     * Registers the resolver used to bind {@code PageQuery} controller parameters.
     *
     * <p>One resolver keeps paging syntax and input errors consistent across resources.
     *
     * @param resolvers the mutable list of additional MVC argument resolvers
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new PageQueryArgumentResolver());
    }
}
