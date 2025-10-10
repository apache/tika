package org.apache.tika.cli;

import org.apache.tika.pipes.core.fetcher.FetcherManager;

public class TikaPipes {
    public static void main(String[] args) throws Exception{
        FetcherManager fetcherManager = FetcherManager.load();
        System.out.println(fetcherManager.getFetcher().getName());
    }
}
