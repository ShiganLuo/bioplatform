package com.bioplatform.config;

import com.github.pagehelper.PageHelper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * MyBatis配置类
 * 配置SqlSessionFactory和PageHelper分页插件
 * 
 * @author luosg
 */
@Configuration
public class MyBatisConfig {

    /**
     * 配置PageHelper分页插件
     * 
     * @return PageHelper实例
     */
    @Bean
    public PageHelper pageHelper() {
        PageHelper pageHelper = new PageHelper();
        Properties properties = new Properties();
        properties.setProperty("helperDialect", "mysql");  // 数据库方言
        properties.setProperty("reasonable", "false");  // 分页合理化：不自动调整页码
        properties.setProperty("supportMethodsArguments", "true");  // 支持通过Mapper接口参数传递分页
        properties.setProperty("params", "count=countSql");  // 自动count查询
        pageHelper.setProperties(properties);
        return pageHelper;
    }

    /**
     * 配置SqlSessionFactory
     * 
     * @param dataSource 数据源
     * @return SqlSessionFactory实例
     * @throws Exception 异常
     */
    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean sessionFactory = new SqlSessionFactoryBean();
        sessionFactory.setDataSource(dataSource);
        
        // 设置Mapper XML文件位置
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        sessionFactory.setMapperLocations(
            resolver.getResources("classpath:mapper/**/*.xml")
        );
        
        // MyBatis配置
        Properties configurationProperties = new Properties();
        configurationProperties.setProperty("mapUnderscoreToCamelCase", "true");  // 下划线转驼峰
        configurationProperties.setProperty("logImpl", "SLF4J");  // 使用SLF4J日志
        configurationProperties.setProperty("callSettersOnNulls", "true");  // null值也调用setter
        configurationProperties.setProperty("defaultEnumTypeHandler", 
            "org.apache.ibatis.type.EnumTypeHandler");  // 枚举类型处理
        
        org.apache.ibatis.session.Configuration configuration = 
            new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLogImpl(org.apache.ibatis.logging.slf4j.Slf4jImpl.class);
        configuration.setCallSettersOnNulls(true);
        configuration.setDefaultEnumTypeHandler(org.apache.ibatis.type.EnumTypeHandler.class);
        
        sessionFactory.setConfiguration(configuration);
        
        return sessionFactory.getObject();
    }
}
