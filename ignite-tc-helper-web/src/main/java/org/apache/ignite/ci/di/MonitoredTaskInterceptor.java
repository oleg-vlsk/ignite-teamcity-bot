/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ignite.ci.di;

import com.google.common.base.Strings;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class MonitoredTaskInterceptor implements MethodInterceptor {
    private final ConcurrentMap<String, Invocation> totalTime = new ConcurrentHashMap<>();

    public static class Invocation {
        private final AtomicLong lastStartTs = new AtomicLong();
        private final AtomicLong lastEndTs = new AtomicLong();
        private final AtomicReference<Object> lastResult = new AtomicReference<>();

        private final AtomicInteger callsCnt = new AtomicInteger();
        private String name;

        public Invocation(String name) {
            this.name = name;
        }

        public void saveStart(long startTs) {
            callsCnt.incrementAndGet();

            lastStartTs.set(startTs);
        }

        public int сount() {
            return callsCnt.get();
        }

        public void saveEnd(long ts, Object res) {
            lastEndTs.set(ts);
            lastResult.set(res);
        }

        public String name() {
            return name;
        }

        public String start() {
            final long l = lastStartTs.get();
            return l == 0 ? "-" : new Date(l).toString();

        }

        public String end() {
            final long l = lastEndTs.get();
            return l == 0 ? "-" : new Date(l).toString();

        }

        public String result() {
            final Object obj = lastResult.get();

            return Objects.toString(obj);
        }
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        final long startTs = System.currentTimeMillis();
        final Method method = invocation.getMethod();

        String fullKey = taskName(method);

        final Invocation monitoredInvoke = totalTime.computeIfAbsent(fullKey, Invocation::new);

        monitoredInvoke.saveStart(startTs);

        Object res = null;
        try {
            res = invocation.proceed();

            return res;
        } catch (Throwable t) {
            res = t;

            throw t;
        } finally {
            monitoredInvoke.saveEnd(System.currentTimeMillis(), res);
        }
    }

    @NotNull
    private String taskName(Method method) {
        String cls = method.getDeclaringClass().getSimpleName();
        String mtd = method.getName();


        String fullKey ;

        final MonitoredTask annotation = method.getAnnotation(MonitoredTask.class);
        if(annotation!=null && !Strings.isNullOrEmpty(annotation.name())) {
            fullKey = annotation.name();
        } else {
            fullKey = cls + "." + mtd;
        }
        return fullKey;
    }

    public Collection<Invocation> getList() {
        return Collections.unmodifiableCollection(totalTime.values());
    }
}
