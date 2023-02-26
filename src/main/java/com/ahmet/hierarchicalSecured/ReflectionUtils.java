package com.ahmet.hierarchicalSecured;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;

public class ReflectionUtils {

    public static <T extends Annotation> T getAnnotation(
            ProceedingJoinPoint pjp, Class<T> annotationClass) throws NoSuchMethodException {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        T annotation = AnnotationUtils.findAnnotation(method, annotationClass);

        if (annotation != null) {
            return annotation;
        }

        method =
                pjp.getTarget()
                        .getClass()
                        .getMethod(pjp.getSignature().getName(), signature.getParameterTypes());
        return AnnotationUtils.findAnnotation(method, annotationClass);
    }
}
