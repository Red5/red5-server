package org.red5.codec;

import java.io.Serializable;

import org.red5.io.amf3.IDataInput;
import org.red5.io.amf3.IDataOutput;
import org.red5.io.amf3.IExternalizable;

/**
 * Color info event
 *
 * @author mondain
 */
public class ColorInfo implements IExternalizable {

    /*
    type ColorInfo = {
        colorConfig: {
          // number of bits used to record the color channels for each pixel
          bitDepth:                 number, // SHOULD be 8, 10 or 12

          //
          // colorPrimaries, transferCharacteristics and matrixCoefficients are defined
          // in ISO/IEC 23091-4/ITU-T H.273. The values are an index into
          // respective tables which are described in "Colour primaries",
          // "Transfer characteristics" and "Matrix coefficients" sections.
          // It is RECOMMENDED to provide these values.
          //

          // indicates the chromaticity coordinates of the source color primaries
          colorPrimaries:           number, // enumeration [0-255]

          // opto-electronic transfer characteristic function (ex. PQ, HLG)
          transferCharacteristics:  number, // enumeration [0-255]

          // matrix coefficients used in deriving luma and chroma signals
          matrixCoefficients:       number, // enumeration [0-255]
        },

        hdrCll: {
          //
          // maximum value of the frame average light level
          // (in 1 cd/m2) of the entire playback sequence
          //
          maxFall:  number,     // [0.0001-10000]

          //
          // maximum light level of any single pixel (in 1 cd/m2)
          // of the entire playback sequence
          //
          maxCLL:   number,     // [0.0001-10000]
        },

        //
        // The hdrMdcv object defines mastering display (i.e., where
        // creative work is done during the mastering process) color volume (a.k.a., mdcv)
        // metadata which describes primaries, white point and min/max luminance. The
        // hdrMdcv object SHOULD be provided.
        //
        // Specification of the metadata along with its ranges adhere to the
        // ST 2086:2018 - SMPTE Standard (except for minLuminance see
        // comments below)
        //
        hdrMdcv: {
          //
          // Mastering display color volume (mdcv) xy Chromaticity Coordinates within CIE
          // 1931 color space.
          //
          // Values SHALL be specified with four decimal places. The x coordinate SHALL
          // be in the range [0.0001, 0.7400]. The y coordinate SHALL be
          // in the range [0.0001, 0.8400].
          //
          redX:         number,
          redY:         number,
          greenX:       number,
          greenY:       number,
          blueX:        number,
          blueY:        number,
          whitePointX:  number,
          whitePointY:  number,

          //
          // max/min display luminance of the mastering display (in 1 cd/m2 ie. nits)
          //
          // note: ST 2086:2018 - SMPTE Standard specifies minimum display mastering
          // luminance in multiples of 0.0001 cd/m2.
          //
          // For consistency we specify all values
          // in 1 cd/m2. Given that a hypothetical perfect screen has a peak brightness
          // of 10,000 nits and a black level of .0005 nits we do not need to
          // switch units to 0.0001 cd/m2 to increase resolution on the lower end of the
          // minLuminance property. The ranges (in nits) mentioned below suffice
          // the theoretical limit for Mastering Reference Displays and adhere to the
          // SMPTE ST 2084 standard (a.k.a., PQ) which is capable of representing full gamut
          // of luminance level.
          //
          maxLuminance: number,     // [5-10000]
          minLuminance: number,     // [0.0001-5]
        },
      }
     */

    private ColorConfig colorConfig;

    private HdrCll hdrCll;

    private HdrMdcv hdrMdcv;

    /**
     * <p>Getter for the field <code>colorConfig</code>.</p>
     *
     * @return a {@link org.red5.codec.ColorInfo.ColorConfig} object
     */
    public ColorConfig getColorConfig() {
        return colorConfig;
    }

    /**
     * <p>Setter for the field <code>colorConfig</code>.</p>
     *
     * @param colorConfig a {@link org.red5.codec.ColorInfo.ColorConfig} object
     */
    public void setColorConfig(ColorConfig colorConfig) {
        this.colorConfig = colorConfig;
    }

    /**
     * <p>Getter for the field <code>hdrCll</code>.</p>
     *
     * @return a {@link org.red5.codec.ColorInfo.HdrCll} object
     */
    public HdrCll getHdrCll() {
        return hdrCll;
    }

    /**
     * <p>Setter for the field <code>hdrCll</code>.</p>
     *
     * @param hdrCll a {@link org.red5.codec.ColorInfo.HdrCll} object
     */
    public void setHdrCll(HdrCll hdrCll) {
        this.hdrCll = hdrCll;
    }

    /**
     * <p>Getter for the field <code>hdrMdcv</code>.</p>
     *
     * @return a {@link org.red5.codec.ColorInfo.HdrMdcv} object
     */
    public HdrMdcv getHdrMdcv() {
        return hdrMdcv;
    }

    /**
     * <p>Setter for the field <code>hdrMdcv</code>.</p>
     *
     * @param hdrMdcv a {@link org.red5.codec.ColorInfo.HdrMdcv} object
     */
    public void setHdrMdcv(HdrMdcv hdrMdcv) {
        this.hdrMdcv = hdrMdcv;
    }

    /** {@inheritDoc} */
    @Override
    public void readExternal(IDataInput in) {
        colorConfig = (ColorConfig) in.readObject();
        hdrCll = (HdrCll) in.readObject();
        hdrMdcv = (HdrMdcv) in.readObject();
    }

    /** {@inheritDoc} */
    @Override
    public void writeExternal(IDataOutput out) {
        out.writeObject(colorConfig);
        out.writeObject(hdrCll);
        out.writeObject(hdrMdcv);
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "ColorInfo [colorConfig=" + colorConfig + ", hdrCll=" + hdrCll + ", hdrMdcv=" + hdrMdcv + "]";
    }

    public class ColorConfig implements Serializable {

        // number of bits used to record the color channels for each pixel
        Number bitDepth = 8; // SHOULD be 8, 10 or 12

        // colorPrimaries, transferCharacteristics and matrixCoefficients are defined in ISO/IEC 23091-4/ITU-T H.273.
        // The values are an index into respective tables which are described in "Colour primaries", "Transfer
        // characteristics" and "Matrix coefficients" sections. It is RECOMMENDED to provide these values.

        // indicates the chromaticity coordinates of the source color primaries
        Number colorPrimaries; // enumeration [0-255]

        // opto-electronic transfer characteristic function (ex. PQ, HLG)
        Number transferCharacteristics; // enumeration [0-255]

        // matrix coefficients used in deriving luma and chroma signals
        Number matrixCoefficients; // enumeration [0-255]

        @Override
        public String toString() {
            return "ColorConfig [bitDepth=" + bitDepth + ", colorPrimaries=" + colorPrimaries + ", transferCharacteristics=" + transferCharacteristics + ", matrixCoefficients=" + matrixCoefficients + "]";
        }

    }

    public class HdrCll implements Serializable {

        // maximum value of the frame average light level (in 1 cd/m2) of the entire playback sequence
        Number maxFall; // [0.0001-10000]

        // maximum light level of any single pixel (in 1 cd/m2) of the entire playback sequence
        Number maxCLL; // [0.0001-10000]

        @Override
        public String toString() {
            return "HdrCll [maxFall=" + maxFall + ", maxCLL=" + maxCLL + "]";
        }

    }

    public class HdrMdcv implements Serializable {

        // Mastering display color volume (mdcv) xy Chromaticity Coordinates within CIE 1931 color space.

        // Values SHALL be specified with four decimal places. The x coordinate SHALL be in the range [0.0001, 0.7400].
        // The y coordinate SHALL be in the range [0.0001, 0.8400].
        Number redX, redY, greenX, greenY, blueX, blueY, whitePointX, whitePointY;

        // max/min display luminance of the mastering display (in 1 cd/m2 ie. nits)
        // note: ST 2086:2018 - SMPTE Standard specifies minimum display mastering luminance in multiples of 0.0001
        // cd/m2.
        //
        // For consistency we specify all values in 1 cd/m2. Given that a hypothetical perfect screen has a peak
        // brightness of 10,000 nits and a black level of .0005 nits we do not need to switch units to 0.0001 cd/m2
        // to increase resolution on the lower end of the minLuminance property. The ranges (in nits) mentioned
        // below suffice the theoretical limit for Mastering Reference Displays and adhere to the SMPTE ST 2084
        // standard (a.k.a., PQ) which is capable of representing full gamut of luminance level.
        Number maxLuminance, // [5-10000]
                minLuminance; // [0.0001-5]

        @Override
        public String toString() {
            return "HdrMdcv [redX=" + redX + ", redY=" + redY + ", greenX=" + greenX + ", greenY=" + greenY + ", blueX=" + blueX + ", blueY=" + blueY + ", whitePointX=" + whitePointX + ", whitePointY=" + whitePointY + ", maxLuminance=" + maxLuminance + ", minLuminance=" + minLuminance + "]";
        }

    }

}
