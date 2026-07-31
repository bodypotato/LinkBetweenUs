package com.body.linkbetweenus;

import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.annotation.MapperScans;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@MapperScan("com.body.linkbetweenus.mvc.mapper")
@EnableCaching
public class LinkBetweenUsApplication {

    public static void main(String[] args) {
        SpringApplication.run(LinkBetweenUsApplication.class, args);
    }

}