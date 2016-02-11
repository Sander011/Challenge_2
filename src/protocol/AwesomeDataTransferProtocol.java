package protocol;

import client.Utils;

import java.util.*;

public class AwesomeDataTransferProtocol extends IRDTProtocol {
    // change the following as you wish:
    static final int HEADERSIZE = 2;   // number of header bytes in each packet
    static final int DATASIZE = 128;   // max. number of user data bytes in each packet
    private static ArrayList<Integer[]> packets;

    private static Timer timer;

    private static Map<Integer, Integer[]> received;
    private static ArrayList<Integer> missing;
    private static int amountOfPackets = 0;


    private void send(int packetNo) {
        if(packetNo != 0) {
            System.out.println("Sent one packet with header=" + (packets.get(packetNo)[0]));
            getNetworkLayer().sendPacket(packets.get(packetNo));
        } else {
            System.out.println("Sent one packet with header=" + 0);
            getNetworkLayer().sendPacket(packets.get(packets.size()-1));
        }

    }

    @Override
    public void sender() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    Integer[] packet = getNetworkLayer().receivePacket();
                    if (packet != null) {
                        System.out.println("Missing acknowledgement for packet: " + packet[0]);
                        send(packet[0]);
                    } else {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException ignored) {}
                    }
                }
            }
        }).start();

        System.out.println("Sending...");

        // read from the input file
        Integer[] fileContents = Utils.getFileContents(getFileID());

        Integer[] newFile = new Integer[fileContents.length];

        // keep track of where we are in the data
        int filePointer = 0;

        int headerCount = 1;

        packets = new ArrayList<>();
        packets.add(new Integer[0]);

        while(filePointer != fileContents.length) {
            int datalen = Math.min(DATASIZE, fileContents.length - filePointer);
            Integer[] pkt = new Integer[HEADERSIZE + datalen];
            if(filePointer + datalen < fileContents.length) {
                pkt[0] = headerCount;
            } else {
                pkt[0] = 0;
            }
            System.arraycopy(fileContents, filePointer, pkt, HEADERSIZE, datalen);
            packets.add(pkt);

            filePointer += datalen;
            headerCount++;
        }

        for (int i = 1; i < packets.size(); i++) {
            send(i);
        }

        // and loop and sleep; you may use this loop to check for incoming acks...
        boolean stop = false;
        while (!stop) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                stop = true;
            }
        }

    }

    @Override
    public void TimeoutElapsed(Object tag) {
        request(missing);
    }

    private void request(ArrayList<Integer> missing) {
        System.out.println("Requesting: " + missing.size() + " packets.");
        missing.add(0, missing.size());
        Integer[] packet = missing.toArray(new Integer[missing.size()]);
        getNetworkLayer().sendPacket(packet);
    }

    private void parsePacket(Integer[] packet) {
        try {
            timer.cancel();
        } catch (IllegalStateException ignored) {}
        int header = packet[0];
        System.out.println("Parsing packet, length="+packet.length+"  first byte="+header);
        if (amountOfPackets == 0) {
            System.out.println(header);
            setAmount(packet[1]);
        }
        received.put(header, Arrays.copyOfRange(packet, HEADERSIZE, packet.length));
        missing.remove(new Integer(header));
    }

    private void setAmount(int amount) {
        amountOfPackets = amount;

        for (int i = 0; i < amountOfPackets; i++) {
            missing.add(i);
        }
    }

    private boolean checkCorrectness () {
        if (amountOfPackets == 0) {
            return false;
        }
        for (int i = 0; i < amountOfPackets; i++) {
            if (!received.containsKey(i)) {
                return false;
            }
        }

        return true;
    }

    private class Reader extends Thread {
        private final AwesomeDataTransferProtocol p;

        public Reader(AwesomeDataTransferProtocol p) {
            this.p = p;
        }

        @Override
        public void run() {
            while (true) {
                Integer[] packet = getNetworkLayer().receivePacket();
                if (packet != null) {
                    System.out.println("Received packet, length="+packet.length+"  first byte="+packet[0]);
                    p.parsePacket(packet);
                } else {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException ignored) {}
                }
            }
        }
    }

    private class Requester extends Thread {
        @Override
        public void run() {
            try {
                Thread.sleep(120000);
            } catch (InterruptedException ignored) {}
            request(missing);
            while (true) {
                try {
                    int size = missing.size();
                    System.out.println(size);
                    System.out.println(amountOfPackets);
                    int time = 10000 + ((int)Math.floor(((double)size / (double)amountOfPackets) * 80000));
                    System.out.println(time);
                    Thread.sleep(time);
                } catch (InterruptedException ignored) {
                }

                request(missing);
            }
        }
    }

    @Override
    public void receiver() {
        System.out.println("Receiving...");

        this.timer = new Timer();

        this.received = new HashMap<>();
        this.missing = new ArrayList<>();

        new Reader(this).start();
        new Requester().start();

        // create the array that will contain the file contents
        // note: we don't know yet how large the file will be, so the easiest (but not most efficient)
        //   is to reallocate the array every time we find out there's more data

        while (amountOfPackets == 0 || !checkCorrectness()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {}
        }


        ArrayList<Integer> keys = new ArrayList<>(received.keySet());
        Collections.sort(keys);

        if (keys.get(0) == 0) {
            keys.add(keys.remove(0));
        }

        Integer[] fileContents = new Integer[0];

        String n = "end";

        for (Integer key : keys) {
            n += " " + key;
            Integer[] packet = received.get(key);
            int oldlength=fileContents.length;
            int datalen= packet.length;
            fileContents = Arrays.copyOf(fileContents, oldlength+datalen);
            System.arraycopy(packet, 0, fileContents, oldlength, datalen);
        }

        System.out.println(n);

        // write to the output file
        Utils.setFileContents(fileContents, getFileID());
    }
}
