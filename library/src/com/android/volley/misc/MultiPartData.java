package com.android.volley.misc;

/**
 * A representation of a MultiPart data part
 */

public class MultiPartData {
    private String mName;
    private byte[] mContent;
    private String mType;

    /**
     * Default data part
     */
    public MultiPartData() {
    }

    /**
     * Constructor with data.
     *
     * @param name label of data
     * @param data byte data
     */
    public MultiPartData(String name, byte[] data) {
        mName = name;
        mContent = data;
    }

    /**
     * Constructor with mime data type.
     *
     * @param name     label of data
     * @param data     byte data
     * @param mimeType mime data like "image/jpeg"
     */
    public MultiPartData(String name, byte[] data, String mimeType) {
        mName = name;
        mContent = data;
        mType = mimeType;
    }

    /**
     * Getter name associated with data. Usually it is file name.
     *
     * @return name associated with data.
     */
    public String getName() {
        return mName;
    }

    /**
     * Setter data name.
     *
     * @param name string Usually it is file name
     */
    public void setName(String name) {
        this.mName = name;
    }

    /**
     * Getter content.
     *
     * @return byte data content in raw byte
     */
    public byte[] getContent() {
        return mContent;
    }

    /**
     * Setter content.
     *
     * @param content raw byte data
     */
    public void setContent(byte[] content) {
        mContent = content;
    }

    /**
     * Getter mime type.
     *
     * @return mime type
     */
    public String getType() {
        return mType;
    }

    /**
     * Setter mime type.
     *
     * @param type mime type
     */
    public void setType(String type) {
        mType = type;
    }

}
