package org.apache.tika.renderer.pdf;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.renderer.RenderResults;
import org.apache.tika.renderer.RenderingState;

public class PDFRenderingState extends RenderingState {

    private TikaInputStream tis;

    private RenderResults renderResults;

    public PDFRenderingState(TikaInputStream tis) {
        this.tis = tis;
    }

    public TikaInputStream getTikaInputStream() {
        return tis;
    }


    public void setRenderResults(RenderResults renderResults) {
        this.renderResults = renderResults;
    }

    public RenderResults getRenderResults() {
        return renderResults;
    }
}
