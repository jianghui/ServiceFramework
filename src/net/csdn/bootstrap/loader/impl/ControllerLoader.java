package net.csdn.bootstrap.loader.impl;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import javassist.CtClass;
import net.csdn.ServiceFramwork;
import net.csdn.annotation.rest.At;
import net.csdn.annotation.rest.ErrorAction;
import net.csdn.annotation.rest.NoAction;
import net.csdn.bootstrap.loader.Loader;
import net.csdn.common.collect.Tuple;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.scan.ScanService;
import net.csdn.common.settings.Settings;
import net.csdn.constants.CError;
import net.csdn.enhancer.ControllerEnhancer;
import net.csdn.filter.FilterEnhancer;
import net.csdn.modules.http.ApplicationController;
import net.csdn.modules.http.RestController;
import net.csdn.modules.http.RestRequest;

import java.io.DataInputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static net.csdn.common.collections.WowCollections.list;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-7-2
 * Time: 上午11:31
 */
public class ControllerLoader implements Loader {

    private final static CSLogger logger = Loggers.getLogger(ControllerLoader.class);

    @Override
    public void load(Settings settings) throws Exception {
        final List<Module> moduleList = new ArrayList<Module>();

        final List<CtClass> controllers = list();
        final ControllerEnhancer enhancer = new FilterEnhancer(settings);
        //自动加载所有Action类
        ServiceFramwork.scanService.scanArchives(settings.get("application.controller"), new ScanService.LoadClassEnhanceCallBack() {
            @Override
            public Class loaded(DataInputStream classFile) {
                try {
                    CtClass ctClass = enhancer.enhanceThisClass(classFile);
                    controllers.add(ctClass);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                return null;
            }
        });

        enhancer.enhanceThisClass2(controllers);

        for (CtClass ctClass : controllers) {
            if (Modifier.isAbstract(ctClass.getModifiers())) continue;
            moduleList.add(bindAction(Class.forName(ctClass.getName())));
        }

        ServiceFramwork.injector = ServiceFramwork.injector.createChildInjector(moduleList);
    }


    private static Module bindAction(final Class clzz) {
        return new AbstractModule() {
            @Override
            protected void configure() {
                if (clzz == null) return;
                try {
                    boolean isController = false;
                    Class wow = clzz;
                    while (wow.getSuperclass() != null) {
                        if (wow.getSuperclass() == ApplicationController.class) {
                            isController = true;
                            break;
                        }
                        wow = wow.getSuperclass();
                    }
                    if (!isController) return;
                    Method[] methods = clzz.getDeclaredMethods();

                    for (Method method : methods) {
                        if (method.getModifiers() == Modifier.PRIVATE) continue;
                        RestController restController = ServiceFramwork.injector.getInstance(RestController.class);

                        NoAction noAction = method.getAnnotation(NoAction.class);
                        if (noAction != null) {
                            restController.setDefaultHandlerKey(new Tuple<Class<ApplicationController>, Method>(clzz, method));
                        }

                        ErrorAction errorAction = method.getAnnotation(ErrorAction.class);
                        if (errorAction != null) {
                            restController.setErrorHandlerKey(new Tuple<Class<ApplicationController>, Method>(clzz, method));
                        }

                        At at = method.getAnnotation(At.class);
                        if (at == null) continue;
                        String url = at.path()[0];
                        RestRequest.Method[] httpMethods = at.types();

                        for (RestRequest.Method httpMethod : httpMethods) {
                            Tuple<Class<ApplicationController>, Method> tuple = new Tuple<Class<ApplicationController>, Method>(clzz, method);
                            restController.registerHandler(httpMethod, url, tuple);
                        }
                        bind(clzz);
                    }
                } catch (Exception e) {
                    logger.error(CError.SystemInitializeError, e);
                }
            }
        };
    }
}
