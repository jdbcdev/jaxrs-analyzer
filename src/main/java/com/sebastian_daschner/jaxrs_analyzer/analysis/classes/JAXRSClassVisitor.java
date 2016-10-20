package com.sebastian_daschner.jaxrs_analyzer.analysis.classes;

import static com.sebastian_daschner.jaxrs_analyzer.model.JavaUtils.isAnnotationPresent;
import static org.objectweb.asm.Opcodes.ACC_NATIVE;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SYNTHETIC;
import static org.objectweb.asm.Opcodes.ASM5;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Stream;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

import com.sebastian_daschner.jaxrs_analyzer.LogProvider;
import com.sebastian_daschner.jaxrs_analyzer.analysis.ProjectAnalyzer.ThreadLocalClassLoader;
import com.sebastian_daschner.jaxrs_analyzer.analysis.classes.annotation.ApplicationPathAnnotationVisitor;
import com.sebastian_daschner.jaxrs_analyzer.analysis.classes.annotation.ConsumesAnnotationVisitor;
import com.sebastian_daschner.jaxrs_analyzer.analysis.classes.annotation.PathAnnotationVisitor;
import com.sebastian_daschner.jaxrs_analyzer.analysis.classes.annotation.ProducesAnnotationVisitor;
import com.sebastian_daschner.jaxrs_analyzer.model.JavaUtils;
import com.sebastian_daschner.jaxrs_analyzer.model.Types;
import com.sebastian_daschner.jaxrs_analyzer.model.results.ClassResult;
import com.sebastian_daschner.jaxrs_analyzer.model.results.MethodResult;

/**
 * @author Sebastian Daschner
 */
public class JAXRSClassVisitor extends ClassVisitor {

    private static final Class<? extends Annotation>[] RELEVANT_METHOD_ANNOTATIONS = new Class[]{Path.class, GET.class, PUT.class, POST.class, DELETE.class, OPTIONS.class, HEAD.class};

    private final ClassResult classResult;

    public JAXRSClassVisitor(final ClassResult classResult) {
        super(ASM5);
        this.classResult = classResult;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        classResult.setOriginalClass(name);
        // TODO see superclasses / interfaces for potential annotations later
    }

    @Override
    public void visitSource(String source, String debug) {
        // TODO can be used for JavaDoc parsing
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        switch (desc) {
            case Types.PATH:
                return new PathAnnotationVisitor(classResult);
            case Types.APPLICATION_PATH:
                return new ApplicationPathAnnotationVisitor(classResult);
            case Types.CONSUMES:
                return new ConsumesAnnotationVisitor(classResult);
            case Types.PRODUCES:
                return new ProducesAnnotationVisitor(classResult);
            default:
                return null;
        }
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        if ((access & ACC_STATIC) == 0)
            return new JAXRSFieldVisitor(classResult, desc, signature);
        return null;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        final boolean legalModifiers = ((access & ACC_SYNTHETIC) | (access & ACC_STATIC) | (access & ACC_NATIVE)) == 0;
        final String methodSignature = signature == null ? desc : signature;

        if (legalModifiers && !"<init>".equals(name)) {
            final MethodResult methodResult = new MethodResult();
            if (hasJAXRSAnnotations(classResult.getOriginalClass(), name, methodSignature))
                return new JAXRSMethodVisitor(classResult, classResult.getOriginalClass(), desc, signature, methodResult, true);
            else {
                final Method annotatedSuperMethod = searchAnnotatedSuperMethod(classResult.getOriginalClass(), name, methodSignature);
                if (annotatedSuperMethod != null) {
                    try {
                        return new JAXRSMethodVisitor(classResult, classResult.getOriginalClass(), desc, signature, methodResult, false);
                    } finally {
                        classResult.getMethods().stream().filter(m -> m.equals(methodResult)).findAny().ifPresent(m -> visitJAXRSSuperMethod(annotatedSuperMethod, m));
                    }
                }
            }
        }
        return null;
    }

    private static boolean hasJAXRSAnnotations(final String className, final String methodName, final String signature) {
        final Method method = JavaUtils.findMethod(className, methodName, signature);
        return method != null && hasJAXRSAnnotations(method);
    }

    private static Method searchAnnotatedSuperMethod(final String className, final String methodName, final String methodSignature) {
        final List<Class<?>> superTypes = determineSuperTypes(className);
        return superTypes.stream().map(c -> {
            final Method superAnnotatedMethod = JavaUtils.findMethod(c, methodName, methodSignature);
            if (superAnnotatedMethod != null && hasJAXRSAnnotations(superAnnotatedMethod))
                return superAnnotatedMethod;
            return null;
        }).filter(Objects::nonNull).findAny().orElse(null);
    }

    private static List<Class<?>> determineSuperTypes(final String className) {
        final Class<?> loadedClass = JavaUtils.loadClassFromName(className);
        if (loadedClass == null)
            return Collections.emptyList();

        final List<Class<?>> superClasses = new ArrayList<>();
        final Queue<Class<?>> classesToCheck = new LinkedBlockingQueue<>();
        Class<?> currentClass = loadedClass;

        do {
            if (currentClass.getSuperclass() != null && Object.class != currentClass.getSuperclass())
                classesToCheck.add(currentClass.getSuperclass());

            Stream.of(currentClass.getInterfaces()).forEach(classesToCheck::add);

            if (currentClass != loadedClass)
                superClasses.add(currentClass);

        } while ((currentClass = classesToCheck.poll()) != null);

        return superClasses;
    }

    private static boolean hasJAXRSAnnotations(final Method method) {
        for (final Object annotation : method.getDeclaredAnnotations()) {
            // TODO test both
            if (Stream.of(RELEVANT_METHOD_ANNOTATIONS).map(a -> JavaUtils.getAnnotation(method, a))
                    .filter(Objects::nonNull).anyMatch(a -> a.getClass().isAssignableFrom(annotation.getClass())))
                return true;

            if (isAnnotationPresent(annotation.getClass(), HttpMethod.class))
                return true;
        }
        return false;
    }

    private void visitJAXRSSuperMethod(Method method, MethodResult methodResult) {
        try {

            final ClassReader classReader = ThreadLocalClassLoader.getClassReader(method.getDeclaringClass().getCanonicalName());
            final ClassVisitor visitor = new JAXRSAnnotatedSuperMethodClassVisitor(methodResult, method);

            classReader.accept(visitor, ClassReader.EXPAND_FRAMES);
        } catch (IOException e) {
            LogProvider.error("Could not analyze JAX-RS super annotated method " + method);
            LogProvider.debug(e);
        }
    }

}

