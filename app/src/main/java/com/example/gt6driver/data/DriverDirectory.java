package com.example.gt6driver.data;

import android.util.SparseArray;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Central directory for numeric driver codes → driver names. */
public final class DriverDirectory {

    /** Simple pair for iteration/populating UI. */
    public static class Entry {
        public final int number;
        public final String name;
        public Entry(int number, String name) {
            this.number = number;
            this.name = name;
        }
        @Override public String toString() { return name; }
    }

    private static final SparseArray<String> MAP = new SparseArray<>();
    private static final Map<Integer, String> UNMODIFIABLE;

    // Scottsdale 2026 List - from DriverList-Tuesday.csv (132 drivers) - will change to API
    static {
        // Defensive: ensure we don't accidentally accumulate entries if someone adds another static block later.
        MAP.clear();

        put(216, "Amy Brodzinski");
        put(115, "Andrew Frederick");
        put(33, "Andy Leoni");
        put(110, "Andy Russell");
        put(35, "Andy Tolmachoff");
        put(131, "Angela Perry");
        put(36, "Arthur Avery Jr");
        put(158, "Arthur Barcelo");
        put(37, "Bernhard Neumann");
        put(38, "Bill Caldwell");
        put(39, "Bob Fouraker");
        put(9, "Bob Hoffman");
        put(41, "Bob Kussard");
        put(40, "Bob Reece");
        put(11, "Bobby Owens");
        put(43, "Brett Williams");
        put(31, "Brian Breen");
        put(42, "Brian Cain");
        put(118, "Brian Kotula");
        put(18, "Brooke Barr");
        put(132, "Bruce Story");
        put(44, "Bryce Albertsen");
        put(119, "Bryant Miller");
        put(215, "Candace Prewitt");
        put(45, "Chad Erwin");
        put(46, "Chad Kline");
        put(47, "Charlie Howarth");
        put(48, "Charlie Hughes");
        put(159, "Chelsea Craigs");
        put(49, "Cherie Costello");
        put(13, "Chris Leoni");
        put(14, "Chris Mattson");
        put(51, "Christopher Hays");
        put(7, "Collin Flatt");
        put(52, "Cyrus Ringle");
        put(54, "Dan Forseth");
        put(53, "Dan Martinez");
        put(19, "Dan Sundstrum");
        put(57, "David Baker");
        put(59, "David Breen");
        put(150, "David House");
        put(25, "David Ringle");
        put(58, "David Zazueta");
        put(56, "Dave Bryce");
        put(30, "Dave Gawtry");
        put(2, "Dave Kirk");
        put(55, "Dave Klumpp");
        put(10, "Dave Lightburne");
        put(135, "Dave Neumeyer");
        put(60, "Dennis Bruder");
        put(61, "Dirk Matthews");
        put(134, "Doyle Gaines");
        put(62, "Doug Hollingstead");
        put(63, "Earnie Lipps");
        put(64, "Ed Brodzinski");
        put(65, "Ed Dominguez");
        put(66, "Ernie Corral");
        put(67, "Farrel Rasner");
        put(68, "Gabriel Ellington");
        put(34, "Gary Bashe");
        put(17, "Gary Dallmer");
        put(69, "Garry Eastwood");
        put(71, "Gerry Callisen");
        put(152, "George Hammond");
        put(70, "George Smith");
        put(72, "Gilbert Montez");
        put(73, "Gina Squires");
        put(74, "Greg Chambers");
        put(75, "Greg Hubbard");
        put(4, "Greg Schupfer");
        put(128, "Hector Quinones");
        put(8, "Jack Conroy");
        put(23, "Jason Matson");
        put(147, "Jeff Dahlmer");
        put(5, "Jeff Hammond");
        put(20, "Jennifer Ringle");
        put(77, "Jim Costello");
        put(76, "Jim Domenoe");
        put(146, "Jim Ryan");
        put(78, "Jimmy Dustman");
        put(81, "John Gordon");
        put(82, "John Mihalka");
        put(83, "John Miller");
        put(154, "John Stock");
        put(141, "John Veith");
        put(155, "Jon Basham");
        put(163, "Jonathan Hess");
        put(185, "Josh Huggett");
        put(32, "Julie Lander");
        put(178, "Ken Anderson");
        put(143, "Ken Erickson");
        put(84, "Ken Maki");
        put(86, "Katelyn Collison");
        put(85, "Katherine Cox");
        put(171, "Lincoln Belt");
        put(88, "Marcos Aguilar");
        put(89, "Martin Amaya Jr");
        put(90, "Martin Amaya Sr");
        put(136, "Martin Chambers");
        put(91, "Marty Bellanca");
        put(92, "Marty Lea");
        put(149, "Mattie Allen");
        put(151, "Matt Russell");
        put(93, "Matt Yare");
        put(194, "Maya Basham");
        put(100, "Mike Cooper");
        put(95, "Mike Cullen");
        put(101, "Mike Dustman");
        put(98, "Mike Evans");
        put(96, "Mike Hill");
        put(97, "Mike Kniskern");
        put(169, "Mike Nolan");
        put(15, "Mike Padilla");
        put(182, "Mike Richardson");
        put(99, "Mike Samer");
        put(28, "Mike Schupfer");
        put(94, "Mike Stone");
        put(102, "Nathaniel Anderson");
        put(214, "Pavel Kiselev");
        put(29, "Paul Carnevale");
        put(179, "Paul Jacobson");
        put(170, "Paul Schoenborn");
        put(104, "Pete Bergmann");
        put(105, "Pete Carpenter");
        put(103, "Pete Pelletier");
        put(106, "Peter Kirdan");
        put(107, "Phil Miller");
        put(172, "Phil Souza");
        put(161, "Rachel Hobbs");
        put(108, "Randy Lea");
        put(177, "Randy Thompson");
        put(111, "Rick Bell");
        put(153, "Roland Bullerkist");
        put(112, "Roland Smith");
        put(114, "Ron Jones");
        put(113, "Ron Miller");
        put(116, "Ryan Kasprzyk");
        put(160, "Sean Pearce");
        put(191, "Shane McNulty");
        put(117, "Scott Jaeckels");
        put(6, "Scott Thomas");
        put(121, "Steve Medina");
        put(120, "Steve Squires");
        put(123, "Steven Montez");
        put(145, "Steffany Stanfield");
        put(124, "Terry Crawford");
        put(168, "Tony Goe");
        put(125, "Troy Bales");
        put(142, "Troy Pabst");
        put(133, "Tyler Strafford");
        put(129, "Vince Fernandez");
        put(130, "Vince Lackey");
        put(127, "Walt Brodzinski");
        put(126, "Walt Miller");

        // expose as unmodifiable Map<Integer,String>
        Map<Integer, String> tmp = new HashMap<>(MAP.size());
        for (int i = 0; i < MAP.size(); i++) {
            int key = MAP.keyAt(i);
            tmp.put(key, MAP.get(key));
        }
        UNMODIFIABLE = Collections.unmodifiableMap(tmp);
    }

    private static void put(int id, String name) { MAP.put(id, name); }

    /** Returns the driver name for an id, or null if unknown. */
    @Nullable public static String nameFor(int id) { return MAP.get(id); }

    /** True if we have a record for the id. */
    public static boolean contains(int id) { return MAP.get(id) != null; }

    /** Unmodifiable view if you ever want to enumerate. */
    public static Map<Integer, String> asMap() { return UNMODIFIABLE; }

    /** Returns all drivers as an iterable list of (number, name) pairs. */
    public static List<Entry> entries() {
        List<Entry> list = new ArrayList<>(MAP.size());
        for (int i = 0; i < MAP.size(); i++) {
            int key = MAP.keyAt(i);
            list.add(new Entry(key, MAP.get(key)));
        }
        return list;
    }

    /** Convenience: just the names (unordered). */
    public static List<String> names() {
        List<String> out = new ArrayList<>(MAP.size());
        for (int i = 0; i < MAP.size(); i++) out.add(MAP.valueAt(i));
        return out;
    }

    private DriverDirectory() {}
}
