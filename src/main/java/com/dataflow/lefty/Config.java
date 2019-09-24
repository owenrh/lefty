package com.dataflow.lefty;

/**
 * Created by Owen Rees-Hayward on 29/10/2016.
 */
public interface Config {
    int NUM_SPLITS = Runtime.getRuntime().availableProcessors() - 2;
}
