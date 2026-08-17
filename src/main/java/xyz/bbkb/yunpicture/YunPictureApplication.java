package xyz.bbkb.yunpicture;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("xyz.bbkb.yunpicture.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)
@EnableAsync
@EnableScheduling
public class YunPictureApplication {

    public static void main(String[] args) {
        SpringApplication.run(YunPictureApplication.class, args);
    }

}
