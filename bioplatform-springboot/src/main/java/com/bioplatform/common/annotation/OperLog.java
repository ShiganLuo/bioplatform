package com.bioplatform.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom annotation for operation logging.
 * Mark controller methods with this annotation to automatically record operation logs.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperLog {

    /**
     * Operation description, e.g. "创建用户", "删除项目".
     */
    String operation() default "";

    /**
     * Module name, e.g. "用户管理", "项目管理".
     */
    String module() default "";
}
