package org.red5.io.obu;

public class OBPFilmGrainParameters {
    public boolean applyGrain;

    public short grainSeed;

    public boolean updateGrain;

    public byte filmGrainParamsRefIdx;

    public byte numYPoints;

    public byte[] pointYValue = new byte[16];

    public byte[] pointYScaling = new byte[16];

    public boolean chromaScalingFromLuma;

    public byte numCbPoints;

    public byte[] pointCbValue = new byte[16];

    public byte[] pointCbScaling = new byte[16];

    public byte numCrPoints;

    public byte[] pointCrValue = new byte[16];

    public byte[] pointCrScaling = new byte[16];

    public byte grainScalingMinus8;

    public byte arCoeffLag;

    public byte[] arCoeffsYPlus128 = new byte[24];

    public byte[] arCoeffsCbPlus128 = new byte[25];

    public byte[] arCoeffsCrPlus128 = new byte[25];

    public byte arCoeffShiftMinus6;

    public byte grainScaleShift;

    public byte cbMult;

    public byte cbLumaMult;

    public short cbOffset;

    public byte crMult;

    public byte crLumaMult;

    public short crOffset;

    public boolean overlapFlag;

    public boolean clipToRestrictedRange;
}