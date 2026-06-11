package com.yuan.user.service.storage.support;

@FunctionalInterface
public interface StorageCallable<T> {

    T call() throws Exception;
}
