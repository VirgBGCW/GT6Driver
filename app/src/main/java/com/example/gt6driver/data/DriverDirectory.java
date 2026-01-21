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

    // Scottsdale 2026 List - from DriverListTuesday.csv (sorted by First Name)
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
        put(118, "Brian Kotula");
        put(132, "Bruce Story");
        put(119, "Bryant Miller");
        put(44, "Bryce Albertsen");
        put(45, "Chad Erwin");
        put(106, "Charles Williamson");
        put(159, "Chelsea Craigs");
        put(46, "Cherie Costello");
        put(47, "Chip Tally");
        put(127, "Christopher Hayes");
        put(7, "Collin Flatt");
        put(101, "Craig Hamre");
        put(19, "Curt Warner");
        put(48, "Cyrus Ringle");
        put(49, "Dan Forseth");
        put(92, "Darryl Toupkin");
        put(2, "Dave Kirk");
        put(10, "Dave Lightburne");
        put(130, "Dave Neumeyer");
        put(50, "David Bryce");
        put(150, "David House");
        put(121, "David Schalles");
        put(51, "David Zazueta");
        put(20, "Deane Chenoweth");
        put(52, "Dirk Matthews");
        put(95, "Donnie Balentine");
        put(129, "Douglas Hollingshead");
        put(93, "Doyle Gaines");
        put(143, "Dustin Hess");
        put(138, "Edward Brodzinski");
        put(53, "Edward Dominguez");
        put(91, "Eric Carlson");
        put(54, "Ernie Corral");
        put(142, "Evan Gaudette");
        put(16, "Gary Dallmer");
        put(55, "Gary Eastwood");
        put(56, "Gene Lusian");
        put(57, "Gerry Callisen");
        put(58, "Gina Squires");
        put(4, "Greg Schupfer");
        put(8, "Jack Conroy");
        put(145, "Jack Edlund");
        put(217, "James Seifert");
        put(134, "James Troy");
        put(23, "Jan Kindell");
        put(5, "Jeff Hammond");
        put(59, "Jeff Schultz");
        put(22, "Jennifer Ringle");
        put(61, "Jim Costello");
        put(147, "Jim Miller");
        put(100, "Jim Ryan");
        put(62, "Jimmy Dustman");
        put(63, "Joe Borson");
        put(149, "Joe Pivonka");
        put(98, "Joe Quintanares");
        put(64, "John Gordon");
        put(12, "John Kindell");
        put(135, "John Miller");
        put(105, "John Rorquist");
        put(103, "John Stock");
        put(108, "Jonathan Basham");
        put(109, "Jonathan Hess");
        put(122, "Katelyn Collison");
        put(139, "Katherine Cox");
        put(141, "Kayln Gerling");
        put(97, "Ken Erickson");
        put(65, "Ken Maki");
        put(144, "Kris Ondrejko");
        put(157, "Kyndal Schultz");
        put(114, "Lincoln Belt");
        put(66, "Lloyd Buelt");
        put(67, "Loren Powell");
        put(69, "Martin Amaya Sr.");
        put(94, "Martin Chambers");
        put(153, "Marty Lea");
        put(70, "Matt Yare");
        put(3, "Michael Cooper");
        put(151, "Michael Hill");
        put(152, "Michael Kniskern");
        put(14, "Mike Berry");
        put(32, "Mike Hannam");
        put(15, "Mike Padilla");
        put(71, "Mike Stone");
        put(146, "Mindy Bailey");
        put(72, "Nathaniel Anderson");
        put(117, "Paul Jacobson");
        put(113, "Paul Schoenborn");
        put(214, "Pavel Kiselev");
        put(140, "Pete Pelletier");
        put(73, "Peter Kirdan");
        put(128, "Rachel Hobbs");
        put(74, "Randy Lea");
        put(75, "Randy Solesbee");
        put(116, "Randy Thompson");
        put(77, "Rick Eckenrode");
        put(148, "Robert Galloway");
        put(78, "Roland Smith");
        put(136, "Roman Chiago");
        put(79, "Ron Jones");
        put(183, "Ron Miller");
        put(104, "Ron Perry");
        put(80, "Ryan Ringle");
        put(6, "Scott Thomas");
        put(81, "Scott Tinius");
        put(82, "Sean McNulty");
        put(99, "Steffany Stanfield");
        put(154, "Stephen Medinas");
        put(90, "Steve Oestreich");
        put(83, "Steve Squires");
        put(13, "Theo Lander");
        put(126, "Thomas Fenstemacher");
        put(112, "Tony Goe");
        put(155, "Traver Riggs");
        put(21, "Troy Bales");
        put(96, "Troy Pabst");
        put(156, "Troy Ringle");
        put(133, "Tyler Strafford");
        put(87, "Walt Brodzinski");

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
