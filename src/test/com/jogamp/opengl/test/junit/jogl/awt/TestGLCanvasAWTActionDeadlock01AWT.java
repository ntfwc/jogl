/**
 * Copyright 2012 JogAmp Community. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 * 
 *    1. Redistributions of source code must retain the above copyright notice, this list of
 *       conditions and the following disclaimer.
 * 
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list
 *       of conditions and the following disclaimer in the documentation and/or other materials
 *       provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY JogAmp Community ``AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL JogAmp Community OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 * The views and conclusions contained in the software and documentation are those of the
 * authors and should not be interpreted as representing official policies, either expressed
 * or implied, of JogAmp Community.
 */
 
package com.jogamp.opengl.test.junit.jogl.awt;

import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.GLProfile;
import javax.media.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.Animator;
import com.jogamp.opengl.util.AnimatorBase;
import com.jogamp.opengl.util.FPSAnimator;

import com.jogamp.opengl.test.junit.util.UITestCase;
import com.jogamp.opengl.test.junit.jogl.demos.es2.GearsES2;

import com.jogamp.opengl.test.junit.util.MiscUtils;

import java.applet.Applet;
import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;


public class TestGLCanvasAWTActionDeadlock01AWT extends UITestCase {
    static long durationPerTest = 1000; // ms
    static final int width = 512;
    static final int height = 512;

    GLEventListener gle1 = null;
    GLEventListener gle2 = null;
    
    @Test
    public void test00NoAnimator() throws InterruptedException {
        testImpl(null, 0, false);
    }
    
    @Test
    public void test01Animator() throws InterruptedException {
        testImpl(new Animator(), 0, false);
    }
    
    @Test
    public void test02FPSAnimator() throws InterruptedException {
        testImpl(new FPSAnimator(30), 0, false);
    }
    
    @Test
    public void test02FPSAnimator_RestartOnAWTEDT() throws InterruptedException {
        testImpl(new FPSAnimator(30), 200, false);
    }
    
    @Test
    public void test02FPSAnimator_RestartOnCurrentThread() throws InterruptedException {
        testImpl(new FPSAnimator(30), 200, true);
    }
    
    void testImpl(final AnimatorBase animator, int restartPeriod, boolean restartOnCurrentThread) throws InterruptedException {
        final Frame frame1 = new Frame("Frame 1");
        final Applet applet1 = new Applet() {
            private static final long serialVersionUID = 1L;            
        };
        
        Assert.assertNotNull(frame1);
        frame1.setLayout(null);
        frame1.pack();        
        {
            Insets insets = frame1.getInsets();
            int w = width + insets.left + insets.right;
            int h = height + insets.top + insets.bottom;
            frame1.setSize(w, h);
            
            int usableH = h - insets.top - insets.bottom;
            applet1.setBounds((w - width)/2, insets.top + (usableH - height)/2, width, height);      
        }
        frame1.setLocation(0, 0);
        frame1.setTitle("Generic Title");
        frame1.add(applet1);
                
        frame1.addWindowListener(new WindowAdapter() {
          public void windowClosing(WindowEvent e) {
              dispose(frame1, applet1);
          }
        });    
        
        gle1 = new GLEventListener() {
            boolean justInitialized = true;
            
            @Override
            public void init(GLAutoDrawable drawable) {
                justInitialized = true;
            }

            @Override
            public void dispose(GLAutoDrawable drawable) {
            }

            @Override
            public void display(GLAutoDrawable drawable) {
                if(!justInitialized) {                    
                    // BUG on OSX/CALayer: If frame.setTitle() is issued right after initialization
                    // the call hangs in 
                    //  at apple.awt.CWindow._setTitle(Native Method)
                    //  at apple.awt.CWindow.setTitle(CWindow.java:765) [1.6.0_37, build 1.6.0_37-b06-434-11M3909]
                    //
                    final String msg = "f "+frameCount+", fps "+( null != animator ? animator.getLastFPS() : 0);
                    System.err.println("About to setTitle: CT "+Thread.currentThread().getName()+", "+msg+
                                       frame1+", displayable "+frame1.isDisplayable()+
                                       ", valid "+frame1.isValid()+", visible "+frame1.isVisible());
                    // Thread.dumpStack();
                    frame1.setTitle(msg);
                    
                }
                frameCount++;
                justInitialized=false;
            }

            @Override
            public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
            }            
        };        
        gle2 = new GearsES2();

        GLCanvas glCanvas = createGLCanvas();
        glCanvas.addGLEventListener(gle1);
        glCanvas.addGLEventListener(gle2);
        
        if(null != animator) {
            System.err.println("About to start Animator: CT "+Thread.currentThread().getName());
            animator.setUpdateFPSFrames(60, System.err);
            animator.add(glCanvas);
            animator.start();
        }

        attachGLCanvas(applet1, glCanvas, false);
        
        System.err.println("About to setVisible.0 CT "+Thread.currentThread().getName());
        try {
            javax.swing.SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    System.err.println("About to setVisible.1.0 CT "+Thread.currentThread().getName());
                    frame1.setVisible(true);
                    System.err.println("About to setVisible.1.X CT "+Thread.currentThread().getName());
                }});
        } catch (Throwable t) { 
            t.printStackTrace();
            Assume.assumeNoException(t);
        }
        System.err.println("About to setVisible.X CT "+Thread.currentThread().getName());
        
        final long sleep = 0 < restartPeriod ? restartPeriod : 100;
        long togo = durationPerTest;
        while( 0 < togo ) {
            if(null == animator) {
                glCanvas.display();
            }
            if(0 < restartPeriod) {
                glCanvas = restart(frame1, applet1, glCanvas, restartOnCurrentThread);
            }
            
            Thread.sleep(sleep);
            
            togo -= sleep;
        }
        
        dispose(frame1, applet1);
        if(null != animator) {
            animator.stop();
        }
        
        gle1 = null;                
        gle2 = null;
    }
    
    int frameCount = 0;
    
    GLCanvas createGLCanvas() {
        System.err.println("*** createGLCanvas.0");
        final GLCapabilities caps = new GLCapabilities(GLProfile.getDefault());
        // Iff using offscreen layer, use pbuffer, hence restore onscreen:=true.
        // caps.setPBuffer(true);
        // caps.setOnscreen(true);
        final GLCanvas glCanvas = new GLCanvas(caps);
        glCanvas.setBounds(0, 0, width, height);
        Assert.assertNotNull(glCanvas);
        System.err.println("*** createGLCanvas.X");
        frameCount = 0;
        return glCanvas;        
    }

    void dispose(final Frame frame, final Applet applet) {
        try {
            javax.swing.SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    frame.remove(applet);
                    frame.dispose();
                }});
        } catch (Throwable t) {
            t.printStackTrace();
            Assume.assumeNoException(t);
        }
    }
    
    GLCanvas restart(final Frame frame, final Applet applet, GLCanvas glCanvas, boolean restartOnCurrentThread) throws InterruptedException {
        glCanvas.disposeGLEventListener(gle1, true);
        glCanvas.disposeGLEventListener(gle2, true);
        detachGLCanvas(applet, glCanvas, restartOnCurrentThread);
                    
        glCanvas = createGLCanvas();
        
        attachGLCanvas(applet, glCanvas, restartOnCurrentThread);
        glCanvas.addGLEventListener(gle1);
        glCanvas.addGLEventListener(gle2);
        
        return glCanvas;
    }
    
    void attachGLCanvas(final Applet applet, final GLCanvas glCanvas, boolean restartOnCurrentThread) {
        System.err.println("*** attachGLCanvas.0 on-current-thread "+restartOnCurrentThread+", currentThread "+Thread.currentThread().getName());
        if( restartOnCurrentThread ) {
            applet.setLayout(new BorderLayout());
            applet.add(glCanvas, BorderLayout.CENTER);
            applet.validate();            
        } else {
            try {
                javax.swing.SwingUtilities.invokeAndWait(new Runnable() {
                    public void run() {
                        applet.setLayout(new BorderLayout());
                        applet.add(glCanvas, BorderLayout.CENTER);
                        applet.validate();
                    }});
            } catch (Throwable t) {
                t.printStackTrace();
                Assume.assumeNoException(t);
            }
        }
        System.err.println("*** attachGLCanvas.X");
    }
    
    void detachGLCanvas(final Applet applet, final GLCanvas glCanvas, boolean restartOnCurrentThread) {
        System.err.println("*** detachGLCanvas.0 on-current-thread "+restartOnCurrentThread+", currentThread "+Thread.currentThread().getName());
        if( restartOnCurrentThread ) {
            applet.remove(glCanvas);
            applet.validate();            
        } else {
            try {
                javax.swing.SwingUtilities.invokeAndWait(new Runnable() {
                    public void run() {
                        applet.remove(glCanvas);                        
                        applet.validate();
                    }});
            } catch (Throwable t) {
                t.printStackTrace();
                Assume.assumeNoException(t);
            }
        }
        System.err.println("*** detachGLCanvas.X");
    }
    
    public static void main(String args[]) {
        for(int i=0; i<args.length; i++) {
            if(args[i].equals("-time")) {
                durationPerTest = MiscUtils.atoi(args[++i], (int)durationPerTest);
            }
        }
        org.junit.runner.JUnitCore.main(TestGLCanvasAWTActionDeadlock01AWT.class.getName());
    }
}