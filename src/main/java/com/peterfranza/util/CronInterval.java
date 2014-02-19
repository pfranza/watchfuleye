package com.peterfranza.util;

import static java.lang.annotation.ElementType.TYPE;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;

@Retention(RUNTIME)
@Target(TYPE)
public @interface CronInterval {

	String value() default "0 0 6 1/1 * ? *"; 
	
}
