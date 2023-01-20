/**
 * 
 */
package com.ikanalm.plugins.utils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Utility class
 * 
 * @author frs
 *
 */
public class Utils {

	/**
	 * unzip a zipfile to a destination dir
	 * 
	 * @param zipFileName
	 * @param destDirName
	 * @throws IOException
	 */
	/* TODO : this is a near-copy of be.ikan.lib.util.compressors.zip.ZipCompressor.uncompress(). Should either use
	 * the original method and add it as a dependency, or refactor this to use the Apache Commons Compress library.
	 *  
	 */
    public static void uncompress(String zipFileName, String destDirName) throws IOException {

    	ZipFile zipFile = new ZipFile(zipFileName);
    	FileInputStream fis = null;
    	BufferedInputStream bis = null;
    	ZipInputStream zis = null;
    	
        try {

            // Open the zipfile
            // zipFile = ZipFile(zipFileName);

            // Get the size of each entry
            Map zipEntrySizes = new HashMap<>();
            Enumeration e = zipFile.entries();
            while (e.hasMoreElements()) {
                ZipEntry zipEntry = (ZipEntry) e.nextElement();
                Integer entrysize = (int) (long) zipEntry.getSize();
                zipEntrySizes.put(zipEntry.getName(), entrysize);
            }

            // Open streams
            fis = new FileInputStream(zipFile.getName());
            bis = new BufferedInputStream(fis);
            zis = new ZipInputStream(bis);

            // Start reading zipentries
            ZipEntry zipEntry = null;
            while ((zipEntry = zis.getNextEntry()) != null) {
                
                // Zipentry is a file
                if (!zipEntry.isDirectory()) {

                    // Get the size
                    int size = (int) zipEntry.getSize();
                    if (size == -1) {
                        size = ((Integer) zipEntrySizes.get(zipEntry.getName())).intValue();
                    }

                    // Get the content
                    byte[] buffer = new byte[size];
                    int bytesInBuffer = 0;
                    int bytesRead = 0;
                    while (((int) size - bytesInBuffer) > 0) {
                        bytesRead = zis.read(buffer, bytesInBuffer, size - bytesInBuffer);
                        if (bytesRead == -1) {
                            break;
                        }
                        bytesInBuffer += bytesRead;
                    }

                    String zipEntryName = zipEntry.getName();
                    // replace all "\" with "/"
                    zipEntryName = zipEntryName.replace('\\', '/');

                    // Get the full path name
                    File file = new File(destDirName, zipEntryName);

                    // Create the parent directory
                    if (!file.getParentFile().exists()) {
                        file.getParentFile().mkdirs();
                    }

                    // Save file
                    FileOutputStream fos = new FileOutputStream(file.getPath());
                    fos.write(buffer, 0, bytesInBuffer);
                    fos.close();

                    // Set modification date to the date in the zipEntry
                    file.setLastModified(zipEntry.getTime());
                }
                // Zipentry is a directory
                else {

                    String zipEntryName = zipEntry.getName();
                    // replace all "\" with "/"
                    zipEntryName = zipEntryName.replace('\\', '/');

                    // Create the directory
                    File dir = new File(destDirName, zipEntryName);
                    dir.setLastModified(zipEntry.getTime());
                    if (!dir.exists()) {
                        dir.mkdirs();
                    }
                }
            }


        } finally {
        	try {
        		// Close streams
        		if (zis != null) zis.close();
        		if (bis != null) bis.close();
        		if (fis != null) fis.close(); 
				
			} finally {
	            // Close zipFile
	            zipFile.close();
			}
        	
        }
    }
	
    /**
     * normalize a file path
     * 
     * @param path path to normalize
     * @return the normalized path
     */
    public static String normalizePath(String path) {
    	if (path == null) return null;
    	
    	String result = path.trim().replace("\\", "/");
    	return result;
    }
    
}
