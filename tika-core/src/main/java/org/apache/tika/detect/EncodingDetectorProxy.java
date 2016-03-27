package org.apache.tika.detect;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import org.apache.tika.config.LoadErrorHandler;
import org.apache.tika.metadata.Metadata;

public class EncodingDetectorProxy implements EncodingDetector {

private EncodingDetector detector;
    
    public EncodingDetectorProxy(String encodingDetectorClassName, ClassLoader loader) 
    {
        this(encodingDetectorClassName, loader, LoadErrorHandler.IGNORE);
    }
    
    public EncodingDetectorProxy(String encodingDetectorClassName, ClassLoader loader, LoadErrorHandler handler) 
    {
        try 
        {
            this.detector = (EncodingDetector)Class.forName(encodingDetectorClassName, true, loader).newInstance();
        } 
        catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) 
        {
            handler.handleLoadError(encodingDetectorClassName, e);
        }
    }
    
    @Override
    public Charset detect(InputStream input, Metadata metadata) throws IOException {
        if(detector != null)
        {
            return detector.detect(input, metadata);
        }
        return null;
    }

}
