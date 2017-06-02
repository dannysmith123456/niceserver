package util;

import java.io.File;

/**
 * This is a class used to hold the image data received from the network. Should include the full
 * image, the thumbnail and the image name.
 */
public class ImageData {

    private File fullImage;
    private File thumbnail;
    private File medium;
    private String imageName;

    public ImageData(File fullImage, File thumbnail, File medium, String imageName) {
        this.fullImage = fullImage;
        this.thumbnail = thumbnail;
        this.imageName = imageName;
        this.medium = medium;
    }

    public File getFullImage() {
        return fullImage;
    }

    public File getThumbnail() {
        return thumbnail;
    }

    public File getMedium() {
        return medium;
    }

    public String getImageName() {
        return imageName;
    }

}
