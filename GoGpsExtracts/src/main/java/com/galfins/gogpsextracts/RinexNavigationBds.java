package com.galfins.gogpsextracts;

import android.location.Location;

import com.google.location.suplclient.ephemeris.EphemerisResponse;
import com.google.location.suplclient.ephemeris.GnssEphemeris;
import com.google.location.suplclient.ephemeris.KeplerianModel;
import com.google.location.suplclient.supl.Ephemeris;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

// import java.util.Base64;

/**
 * @author Lorenzo Patocchi, cryms.com
 * <p>
 * This class retrieve RINEX file on-demand from known server structures
 */
public class RinexNavigationBds implements NavigationProducer {

    private static final String SERVICE_IP = "118.31.19.219";
    private static final int SERVICE_PORT = 8999;

    private HashMap<String, RinexNavigationParserBds> pool = new HashMap<String, RinexNavigationParserBds>();
    /**
     * Compute the GPS satellite coordinates
     * <p>
     * INPUT:
     *
     * @param unixTime           = time of measurement reception - UNIX        [milliseconds]
     * @param range              = pseudorange measuremnent                          [meters]
     * @param satID              = satellite ID
     * @param satType            = satellite type indicating the constellation (E: Galileo,
     *                           G: GPS)
     * @param receiverClockError = 0.0
     */
    public SatellitePosition getSatPositionAndVelocities(long unixTime, double range, int satID, char satType, double receiverClockError, Location initialLocation) {

        //long unixTime = obs.getRefTime().getMsec();
        //double range = obs.getSatByIDType(satID, satType).getPseudorange(0);

        RinexNavigationParserBds rnp = getRNPByTimestamp(unixTime, initialLocation);

        if (rnp != null) {
            if (rnp.isTimestampInEpocsRange(unixTime)) {
                return rnp.getSatPositionAndVelocities(unixTime, range, satID, satType, receiverClockError);
            } else {
                return null;
            }
        }

        return null;
    }

    public EphBds findEph(long unixTime, int satID, char satType, Location initialLocation) {
        long requestedTime = unixTime;
        EphBds eph = null;
        int maxBack = 12;
        while (eph == null && (maxBack--) > 0) {

            RinexNavigationParserBds rnp = getRNPByTimestamp(requestedTime, initialLocation);

            if (rnp != null) {
                if (rnp.isTimestampInEpocsRange(unixTime)) {
                    eph = rnp.findEph(unixTime, satID, satType);
                }
            }
            if (eph == null)
                requestedTime -= (1L * 3600L * 1000L);
        }

        return eph;
    }

    protected RinexNavigationParserBds getNavMessage() {
        ArrayList<GnssEphemeris> ephList = new ArrayList<GnssEphemeris>();

        byte[] ephMsg = getBDSEphFromIgmas();
        Ephemeris.IonosphericModelProto.Builder ionoBuilder = Ephemeris.IonosphericModelProto.newBuilder();

        int length = 0;
        int i = 0;
        for (i = 0; i < 4; ++i) {
            double alpha = getDoubleFromArray(ephMsg, length);
            ionoBuilder.addAlpha(alpha);
            length += 8;
        }

        for (i = 0; i < 4; ++i) {
            double beta = getDoubleFromArray(ephMsg, length);
            ionoBuilder.addBeta(beta);
            length += 8;
        }

        Ephemeris.IonosphericModelProto ionoProto = ionoBuilder.build();
        Ephemeris.IonosphericModelProto ionoProto2 = ionoBuilder.build();

        for (i = 0; i < 40; ++i) {
            BdsEphemeris.Builder builder = BdsEphemeris.newBuilder();

            builder.setSvid(i + 1);

            builder.setTtxS(getDoubleFromArray(ephMsg, length));
            length += 8;

            builder.setIode((int) getDoubleFromArray(ephMsg, length));
            length += 8;

            builder.setIodc((int) getDoubleFromArray(ephMsg, length));
            length += 8;

            builder.setAccuracyM(getDoubleFromArray(ephMsg, length));
            length += 8;

            builder.setAf0S(getDoubleFromArray(ephMsg, length));
            length += 8;

            builder.setAf1SecPerSec(getDoubleFromArray(ephMsg, length));
            length += 8;

            builder.setAf2SecPerSec2(getDoubleFromArray(ephMsg, length));
            length += 8;

            KeplerianModel.Builder model = KeplerianModel.newBuilder();

            model.setCrs(getDoubleFromArray(ephMsg, length));
            length += 8;

            model.setDeltaN(getDoubleFromArray(ephMsg, length));
            length += 8;

            model.setM0(getDoubleFromArray(ephMsg, length));
            length += 8;

            model.setCuc(getDoubleFromArray(ephMsg, length));
            length += 8;

            model.setEccentricity(getDoubleFromArray(ephMsg, length));
            length += 8;

            model.setCus(getDoubleFromArray(ephMsg, length));
            length += 8;

            model.setSqrtA(getDoubleFromArray(ephMsg, length));
            length += 8;

            model.setCic(getDoubleFromArray(ephMsg, length));
            length += 8;

            model.setOmega0(getDoubleFromArray(ephMsg, length));
            length += 8;

            model.setCis(getDoubleFromArray(ephMsg, length));
            length += 8;

            model.setI0(getDoubleFromArray(ephMsg, length));
            length += 8;

            model.setCrc(getDoubleFromArray(ephMsg, length));
            length += 8;

            model.setOmega(getDoubleFromArray(ephMsg, length));
            length += 8;

            model.setOmegaDot(getDoubleFromArray(ephMsg, length));
            length += 8;

            model.setIDot(getDoubleFromArray(ephMsg, length));
            length += 8;

            builder.setTgdS(getDoubleFromArray(ephMsg, length));
            length += 8;

            builder.setHealth((int) getDoubleFromArray(ephMsg, length));
            length += 8;

            double toeSec = getDoubleFromArray(ephMsg, length);
            length += 8;
            model.setToeS(toeSec);
            builder.setTocS(toeSec);

            builder.setWeek((int) getDoubleFromArray(ephMsg, length));
            length += 8;

            builder.setKeplerianModel(new KeplerianModel(model));

            ephList.add(builder.build());
        }
        EphemerisResponse ephResponse = new EphemerisResponse(ephList, ionoProto, ionoProto2);
        RinexNavigationParserBds rnp = new RinexNavigationParserBds(ephResponse);
        return rnp;
    }

    private byte[] getBDSEphFromIgmas() {
        byte[] result = new byte[10000];
        try {
            Socket clientSocket = new Socket(SERVICE_IP, SERVICE_PORT);  // clientSocket为定义的套接字
            OutputStream out = clientSocket.getOutputStream();
            out.write(("456").getBytes());
            int length = 8384;
            BufferedInputStream bis = new BufferedInputStream(clientSocket.getInputStream());
            int readLen = 0;
            int getLen = 0;
            while (getLen < length) {
                readLen = bis.read(result, getLen, length - getLen);
                if (readLen == -1) {
                    return null;
                }
                getLen = getLen + readLen;
            }
            clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    private double getDoubleFromArray(byte[] arr, int pos) {
        byte[] temp = new byte[8];
        System.arraycopy(arr, pos, temp, 0, 8);
        return bytes2Double(temp);
    }

    private double bytes2Double(byte[] arr) {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value |= ((long) (arr[i] & 0xff)) << (8 * i);
        }
        return Double.longBitsToDouble(value);
    }

    protected RinexNavigationParserBds getRNPByTimestamp(long unixTime, final Location initialLocation) {
        RinexNavigationParserBds rnp = null;
        if (pool.containsKey("bdsEph")) {
            rnp = pool.get("bdsEph");
        } else {
            rnp = getNavMessage();
            if (rnp != null) {
                pool.put("bdsEph", rnp);
            }
        }
        return rnp;
    }

    /* (non-Javadoc)
     * @see org.gogpsproject.NavigationProducer#getIono(int)
     */
    @Override
    public Iono getIono(long unixTime, Location initialLocation) {
        RinexNavigationParserBds rnp = getRNPByTimestamp(unixTime, initialLocation);
        if (rnp != null) return rnp.getIono(unixTime, initialLocation);
        return null;
    }

    /* (non-Javadoc)
     * @see org.gogpsproject.NavigationProducer#init()
     */
    @Override
    public void init() {

    }

    /* (non-Javadoc)
     * @see org.gogpsproject.NavigationProducer#release()
     */
    @Override
    public void release(boolean waitForThread, long timeoutMs) throws InterruptedException {

    }

    @Override
    public SatellitePosition getGpsSatPosition(Observations obs, int satID, char satType, double receiverClockError) {
        // TODO Auto-generated method stub
        return null;
    }


}

