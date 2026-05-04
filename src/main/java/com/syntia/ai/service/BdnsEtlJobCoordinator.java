package com.syntia.ai.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

@Service
public class BdnsEtlJobCoordinator {

    public enum Job { CATALOGOS, INDICES }

    private final AtomicReference<Job> activo = new AtomicReference<>();

    public boolean iniciar(Job job) {
        return activo.compareAndSet(null, job);
    }

    public void finalizar(Job job) {
        activo.compareAndSet(job, null);
    }

    public boolean estaEnCurso(Job job) {
        return activo.get() == job;
    }
}
