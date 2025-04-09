package com.mongoplus.annotation.tenant;

import java.lang.annotation.*;

/**
 * 忽略多租户注解，优先级高
 *
 * @author anwen
 */
@Target(ElementType.METHOD)
//运行时注解
@Retention(RetentionPolicy.RUNTIME)
//表明这个注解应该被 javadoc工具记录
//生成文档
@Documented
public @interface IgnoreTenant {

}
