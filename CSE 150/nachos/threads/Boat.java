package nachos.threads;

import nachos.ag.BoatGrader;

public class Boat {
  static BoatGrader bg;
  static int passengerCnt = 0;
  static int childrenOnOahu;
  static int adultsOnOahu;
  static int childrenOnMolokai;
  static int adultsOnMolokai;
  static Island boatPlace = Island.Oahu;
  static Island personPlace = Island.Oahu;
  static Communicator comm = new Communicator();
  static Lock boatLock = new Lock();
  static Condition2 OahuWait = new Condition2(boatLock);
  static Condition2 MolokaiWait = new Condition2(boatLock);
  static Condition2 boatFull = new Condition2(boatLock);

  public static void selfTest() {
    BoatGrader b = new BoatGrader();

    //        System.out.println("\n ***Testing Boats with only 2 children***");
    //        begin(0, 2, b);

    //	System.out.println("\n ***Testing Boats with 2 children, 1 adult***");
    //  	begin(1, 2, b);

    //  	System.out.println("\n ***Testing Boats with 3 children, 3 adults***");
    //  	begin(3, 3, b);
  }

  public static void begin(int adults, int children, BoatGrader b) {
    // Store the externally generated autograder in a class
    // variable to be accessible by children.
    bg = b;

    // Instantiate global variables here
    adultsOnOahu = adults;
    childrenOnOahu = children;
    childrenOnMolokai = 0;
    adultsOnMolokai = 0;

    Runnable adultRun =
        new Runnable() {
          // @Override
          public void run() {
            AdultItinerary();
          }
        };

    Runnable childRun =
        new Runnable() {
          // @Override
          public void run() {
            ChildItinerary();
          }
        };

    for (int i = 0; i < children; i++) {
      KThread t = new KThread(childRun);
      t.setName("Child #" + i);
      t.fork();
    }

    for (int j = 0; j < adults; j++) {
      KThread t = new KThread(adultRun);
      t.setName("Adult #" + j);
      t.fork();
    }

    // Create threads here. See section 3.4 of the Nachos for Java
    // Walkthrough linked from the projects page.
    /*
    Runnable r = new Runnable() {
        public void run() {
            SampleItinerary();
        }
    };
    KThread t = new KThread(r);
    t.setName("Sample Boat Thread");
    t.fork();
    */

    // Check if the simulation has ended

    while (true) {
      if (comm.listen() == adults + children) break;
    }
  }

  static void AdultItinerary() {

    // bg.initializeAdult(); //Required for autograder interface. Must be the first thing called.
    // DO NOT PUT ANYTHING ABOVE THIS LINE.

    /* This is where you should put your solutions. Make calls
       to the BoatGrader to show that it is synchronized. For
       example:
           bg.AdultRowToMolokai();
       indicates that an adult has rowed the boat across to Molokai
    */
    boatLock.acquire();

    while (true) {

      if (personPlace == Island.Molokai) {
        // MolokaiWait.sleep();
        break;
      } else if (personPlace == Island.Oahu) {
        /*
         * While the boat is not here or
         * There is not enough space on the boat or
         * There is no guarantee that there is a child on Molokai to bring the boat back
         * Then sleep
         */
        while (boatPlace != Island.Oahu || passengerCnt > 0 || childrenOnOahu > 1) {
          OahuWait.sleep();
        }

        /*
         * Otherwise ride to Molokai
         * Update information
         * One-way communicate with begin() to check if simulation is done
         * And wake up people waiting on Molokai
         */
        bg.AdultRowToMolokai();
        adultsOnOahu--;
        adultsOnMolokai++;
        boatPlace = Island.Molokai;
        personPlace = Island.Molokai;
        comm.speak(adultsOnMolokai + childrenOnMolokai);
        MolokaiWait.wakeAll();
      }
      /* else {
      	// unreachable
      	break;
      }	*/

    }

    boatLock.release();
  }

  static void ChildItinerary() {
    // bg.initializeChild(); //Required for autograder interface. Must be the first thing called.
    // DO NOT PUT ANYTHING ABOVE THIS LINE.

    boolean theEnd = false;

    boatLock.acquire();

    while (true) {
      if (personPlace == Island.Oahu) {
        while (boatPlace != Island.Oahu || passengerCnt > 1 || childrenOnOahu == 1) {
          OahuWait.sleep();
        }

        OahuWait.wakeAll();

        passengerCnt++;

        if (passengerCnt == 1) {
          boatFull.sleep();
          childrenOnOahu--;

          bg.ChildRowToMolokai();

          personPlace = Island.Molokai;
          childrenOnMolokai++;

          boatFull.wakeAll();
          MolokaiWait.sleep();
        } else {
          boatFull.wakeAll();
          boatFull.sleep();

          childrenOnOahu--;

          if (adultsOnOahu == 0 && childrenOnOahu == 0) theEnd = true;

          bg.ChildRideToMolokai();

          passengerCnt = 0;
          boatPlace = Island.Molokai;
          personPlace = Island.Molokai;
          childrenOnMolokai++;

          comm.speak(childrenOnMolokai + adultsOnMolokai);

          if (theEnd) break;

          MolokaiWait.wakeAll();
          MolokaiWait.sleep();
        }
      } else {
        while (boatPlace != Island.Molokai) {
          MolokaiWait.sleep();
        }

        childrenOnMolokai--;

        bg.ChildRowToOahu();
        boatPlace = Island.Oahu;
        personPlace = Island.Oahu;
        childrenOnOahu++;

        OahuWait.wakeAll();
        OahuWait.sleep();
      }
    }

    boatLock.release();
  }

  static void SampleItinerary() {
    // Please note that this isn't a valid solution (you can't fit
    // all of them on the boat). Please also note that you may not
    // have a single thread calculate a solution and then just play
    // it back at the autograder -- you will be caught.
    System.out.println("\n ***Everyone piles on the boat and goes to Molokai***");
    bg.AdultRowToMolokai();
    bg.ChildRideToMolokai();
    bg.AdultRideToMolokai();
    bg.ChildRideToMolokai();
  }

  enum Island {
    Oahu,
    Molokai
  }
}
