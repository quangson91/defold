package com.defold.editor;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javafx.application.Application;
import javafx.stage.Stage;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Start extends Application {

    Logger logger = LoggerFactory.getLogger(Start.class);

    static ArrayList<URL> extractURLs(String classPath) {
        ArrayList<URL> urls = new ArrayList<>();
        for (String s : classPath.split(File.pathSeparator)) {
            String suffix = "";
            if (!s.endsWith(".jar")) {
                suffix = "/";
            }
            URL url;
            try {
                url = new URL(String.format("file:%s%s", s, suffix));
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
            urls.add(url);
        }
        return urls;
    }

    private LinkedBlockingQueue<Object> pool;
    private ThreadPoolExecutor threadPool;
    private File libPath;
    private Future<?> extractFuture;
    private static boolean createdFromMain = false;

    public Start() throws IOException {
        pool = new LinkedBlockingQueue<>(1);
        threadPool = new ThreadPoolExecutor(1, 1, 3000, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>());
        threadPool.allowCoreThreadTimeOut(true);

        extractFuture = threadPool.submit(() -> {
            try {
                extractNativeLibs();
            } catch (Exception e) {
                logger.error("failed to extract native libs", e);
            }
        });
    }

    private static Map<com.defold.editor.Platform, String[]> nativeLibs = new HashMap<>();
    static {
        nativeLibs.put(Platform.X86_64Darwin, new String[] {
                "libjogl_desktop.jnilib", "libjogl_mobile.jnilib",
                "libnativewindow_awt.jnilib", "libnativewindow_macosx.jnilib",
                "libnewt.jnilib", "libgluegen-rt.jnilib" });

        nativeLibs.put(Platform.X86Win32, new String[] { "jogl_desktop.dll",
                "jogl_mobile.dll", "nativewindow_awt.dll",
                "nativewindow_win32.dll", "newt.dll", "gluegen-rt.dll" });

        nativeLibs.put(Platform.X86Linux, new String[] { "libjogl_desktop.so",
                "libjogl_mobile.so", "libnativewindow_awt.so",
                "libnativewindow_x11.so", "libnewt.so", "libgluegen-rt.so" });

        nativeLibs.put(Platform.X86_64Linux, nativeLibs.get(Platform.X86Linux));
        nativeLibs.put(Platform.X86_64Win32, nativeLibs.get(Platform.X86Win32));
    }

    private void extractNativeLib(Platform platform, String name)
            throws IOException {
        String path = String.format("/lib/%s/%s", platform.getPair(), name);
        URL resource = this.getClass().getResource(path);
        if (resource != null) {
            logger.debug("extracting lib {}", path);
            FileUtils.copyURLToFile(resource,
                    new File(libPath, new File(path).getName()));
        }
    }

    private void extractNativeLibs() throws IOException {
        libPath = Files.createTempDirectory(null).toFile();
        logger.debug("java.library.path={}", libPath);

        Platform platform = Platform.getJavaPlatform();
        for (String name : nativeLibs.get(platform)) {
            extractNativeLib(platform, name);
        }

        System.setProperty("java.library.path", libPath.getAbsolutePath());
        System.setProperty("jogamp.gluegen.UseTempJarCache", "false");
    }

    private ClassLoader makeClassLoader() {
        ArrayList<URL> urls = extractURLs(System.getProperty("java.class.path"));
        // The "boot class-loader", i.e. for java.*, sun.*, etc
        ClassLoader parent = ClassLoader.getSystemClassLoader().getParent();
        // Per instance class-loader
        ClassLoader classLoader = new URLClassLoader(urls.toArray(new URL[urls.size()]), parent);
        return classLoader;
    }

    private Object makeEditor() throws Exception {
        ClassLoader classLoader = makeClassLoader();

        // NOTE: Is context classloader required?
        // Thread.currentThread().setContextClassLoader(classLoader);

        Class<?> editorApplicationClass = classLoader.loadClass("com.defold.editor.EditorApplication");
        Object editorApplication = editorApplicationClass.getConstructor(new Class[] { Object.class, ClassLoader.class }).newInstance(this, classLoader);
        return editorApplication;
    }

    private void poolEditor(long delay) {
        FutureTask<Object> future = new FutureTask<>(new Callable<Object>() {

            @Override
            public Object call() throws Exception {
                // Arbitrary sleep in order to reduce the CPU load while loading the project
                Thread.sleep(delay);
                Object editorApplication = makeEditor();
                pool.add(editorApplication);
                return null;
            }

        });
        threadPool.submit(future);
    }

    public void openEditor(String[] args) throws Exception {
        if (!createdFromMain) {
            throw new RuntimeException(String.format("Multiple %s classes. ClassLoader errors?", this.getClass().getName()));
        }
        poolEditor(3000);
        Object editorApplication = pool.take();
        Method run = editorApplication.getClass().getMethod("run", new Class[]{ String[].class });
        run.invoke(editorApplication, new Object[] { args });
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        Splash splash = new Splash();

        FutureTask<Object> future = new FutureTask<>(new Callable<Object>() {

            @Override
            public Object call() throws Exception {

                try {
                    pool.add(makeEditor());
                    javafx.application.Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                extractFuture.get();
                                openEditor(new String[0]);
                                splash.close();
                            } catch (Throwable t) {
                                t.printStackTrace();
                            }
                        }
                    });
                } catch (Throwable t) {
                    t.printStackTrace();
                    String message = (t instanceof InvocationTargetException) ? t
                            .getCause().getMessage() : t.getMessage();
                    javafx.application.Platform.runLater(() -> {
                        splash.setLaunchError(message);
                        splash.setErrorShowing(true);
                    });
                }
                return null;
            }

        });
        threadPool.submit(future);
    }

    public static void main(String[] args) throws Exception {
        createdFromMain = true;
        Start.launch(args);
    }

}
