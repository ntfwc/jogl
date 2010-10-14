/**
 * Copyright 2010 JogAmp Community. All rights reserved.
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

package com.jogamp.opengl.util;

import com.jogamp.common.util.locks.RecursiveLock;
import com.jogamp.opengl.impl.Debug;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.media.opengl.GLAnimatorControl;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLProfile;

/**
 * Base implementation of GLAnimatorControl
 */
public abstract class AnimatorBase implements GLAnimatorControl {
    protected static final boolean DEBUG = Debug.debug("Animator");

    private static int animatorCount = 0;

    protected ArrayList/*<GLAutoDrawable>*/ drawables = new ArrayList();
    protected RecursiveLock drawablesLock = new RecursiveLock();
    protected AnimatorImpl impl;
    protected String baseName;
    protected Thread thread;
    protected boolean ignoreExceptions;
    protected boolean printExceptions;
    protected long startTime;
    protected long curTime;
    protected int  totalFrames;

    /** Creates a new, empty Animator. */
    public AnimatorBase() {
        if(GLProfile.isAWTAvailable()) {
            try {
                impl = (AnimatorImpl) Class.forName("com.jogamp.opengl.util.awt.AWTAnimatorImpl").newInstance();
                baseName = "AWTAnimator";
            } catch (Exception e) { }
        }
        if(null==impl) {
            impl = new AnimatorImpl();
            baseName = "Animator";
        }
        synchronized (Animator.class) {
            animatorCount++;
            baseName = baseName.concat("-"+animatorCount);
        }
        resetCounter();
    }

    protected abstract String getBaseName(String prefix);

    public synchronized void add(GLAutoDrawable drawable) {
        drawablesLock.lock();
        try {
            drawables.add(drawable);
            drawable.setAnimator(this);
        } finally {
            drawablesLock.unlock();
        }
        notifyAll();
    }

    public synchronized void remove(GLAutoDrawable drawable) {
        drawablesLock.lock();
        try {
            drawables.remove(drawable);
            drawable.setAnimator(null);
        } finally {
            drawablesLock.unlock();
        }
        notifyAll();
    }

    /** Called every frame to cause redrawing of all of the
        GLAutoDrawables this Animator manages. Subclasses should call
        this to get the most optimized painting behavior for the set of
        components this Animator manages, in particular when multiple
        lightweight widgets are continually being redrawn. */
    protected void display() {
        impl.display(this, ignoreExceptions, printExceptions);
        curTime = System.currentTimeMillis();
        totalFrames++;
    }

    public List acquireDrawables() {
        drawablesLock.lock();
        return drawables;
    }

    public void releaseDrawables() {
        drawablesLock.unlock();
    }

    public long getCurrentTime() {
        return curTime;
    }

    public long getDuration() {
        return curTime - startTime;
    }

    public long getStartTime() {
        return startTime;
    }

    public int getTotalFrames() {
        return totalFrames;
    }

    public synchronized void resetCounter() {
        startTime = System.currentTimeMillis(); // overwrite startTime to real init one
        curTime   = startTime;
        totalFrames = 0;
    }

    public final synchronized Thread getThread() {
        return thread;
    }

    /** Sets a flag causing this Animator to ignore exceptions produced
    while redrawing the drawables. By default this flag is set to
    false, causing any exception thrown to halt the Animator. */
    public void setIgnoreExceptions(boolean ignoreExceptions) {
        this.ignoreExceptions = ignoreExceptions;
    }

    /** Sets a flag indicating that when exceptions are being ignored by
    this Animator (see {@link #setIgnoreExceptions}), to print the
    exceptions' stack traces for diagnostic information. Defaults to
    false. */
    public void setPrintExceptions(boolean printExceptions) {
        this.printExceptions = printExceptions;
    }

    public String toString() {
        return getClass().getName()+"[started "+isStarted()+", animating "+isAnimating()+", paused "+isPaused()+", frames "+getTotalFrames()+"]";
    }

    protected abstract boolean getShouldPause();

    protected abstract boolean getShouldStop();
}
