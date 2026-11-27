package com.mytune.app.domain

/** An artist the user picked on the onboarding page. */
data class PickedArtist(val id: String, val name: String, val image: String)

/** Everything the personalised home feed needs to know about the user. */
data class UserPrefs(
    val country: String = "India",
    val languages: List<String> = emptyList(),
    val artists: List<PickedArtist> = emptyList(),
)

/** Static lists used by the onboarding page and the home feed. */
object Catalog {
    val countries = listOf(
        "India", "Pakistan", "Bangladesh", "Nepal", "Sri Lanka", "United States",
        "United Kingdom", "Canada", "Australia", "United Arab Emirates", "Singapore", "Other",
    )

    val languages = listOf(
        "Hindi", "English", "Punjabi", "Tamil", "Telugu", "Bengali", "Marathi", "Gujarati",
        "Kannada", "Malayalam", "Bhojpuri", "Haryanvi", "Rajasthani", "Urdu", "Odia", "Assamese",
    )

    /** Well known artists per language: shown on onboarding and used for "Artists you may like". */
    val artistsByLanguage: Map<String, List<String>> = mapOf(
        "Hindi" to listOf("Arijit Singh", "Shreya Ghoshal", "Atif Aslam", "Neha Kakkar", "Sonu Nigam", "Jubin Nautiyal", "Badshah", "Darshan Raval", "Armaan Malik", "Sunidhi Chauhan"),
        "English" to listOf("Ed Sheeran", "Taylor Swift", "The Weeknd", "Justin Bieber", "Billie Eilish", "Dua Lipa", "Bruno Mars", "Coldplay", "Imagine Dragons", "Adele"),
        "Punjabi" to listOf("Diljit Dosanjh", "Sidhu Moose Wala", "AP Dhillon", "Karan Aujla", "Guru Randhawa", "Jassie Gill", "Gippy Grewal", "B Praak"),
        "Tamil" to listOf("Anirudh Ravichander", "A.R. Rahman", "Yuvan Shankar Raja", "Sid Sriram", "Harris Jayaraj", "Dhee", "Shweta Mohan", "G.V. Prakash Kumar"),
        "Telugu" to listOf("Devi Sri Prasad", "S. Thaman", "Anurag Kulkarni", "Armaan Malik", "Mangli", "Sid Sriram", "Ram Miriyala", "Karthik"),
        "Bengali" to listOf("Arijit Singh", "Anupam Roy", "Shreya Ghoshal", "Armaan Malik", "Nachiketa", "Rupam Islam", "Lagnajita Chakraborty", "Rabindra Sangeet"),
        "Marathi" to listOf("Ajay-Atul", "Shankar Mahadevan", "Adarsh Shinde", "Avdhoot Gupte", "Vaishali Made", "Anand Shinde", "Sonu Nigam", "Shreya Ghoshal"),
        "Gujarati" to listOf("Kinjal Dave", "Geeta Rabari", "Jignesh Kaviraj", "Aishwarya Majmudar", "Arvind Vegda", "Parthiv Gohil", "Kirtidan Gadhvi", "Rakesh Barot"),
        "Kannada" to listOf("Vijay Prakash", "Armaan Malik", "Sanjith Hegde", "Arjun Janya", "Rajesh Krishnan", "Shreya Ghoshal", "Sonu Nigam", "Vasuki Vaibhav"),
        "Malayalam" to listOf("K. S. Chithra", "Vineeth Sreenivasan", "Shreya Ghoshal", "Sushin Shyam", "Sithara Krishnakumar", "Vidyasagar", "Gopi Sundar", "Karthik"),
        "Bhojpuri" to listOf("Pawan Singh", "Khesari Lal Yadav", "Dinesh Lal Yadav Nirahua", "Neelkamal Singh", "Shilpi Raj", "Ritesh Pandey", "Kalpana", "Arvind Akela Kallu"),
        "Haryanvi" to listOf("Sapna Choudhary", "Renuka Panwar", "Masoom Sharma", "Ajay Hooda", "Vijay Varma", "Raju Punjabi", "Diler Kharkiya", "Pranjal Dahiya"),
        "Rajasthani" to listOf("Seema Mishra", "Mame Khan", "Kailash Kher", "Gajendra Ajmera", "Shreya Ghoshal", "Anupama Raag", "Ila Arun", "Swaroop Khan"),
        "Urdu" to listOf("Rahat Fateh Ali Khan", "Atif Aslam", "Nusrat Fateh Ali Khan", "Jagjit Singh", "Ghulam Ali", "Abida Parveen", "Mehdi Hassan", "Ali Zafar"),
        "Odia" to listOf("Humane Sagar", "Asima Panda", "Sricharan", "Ananya Sritam Nanda", "Tapu Mishra", "Kumar Bapi", "Abhijit Majumdar", "Sonu Nigam"),
        "Assamese" to listOf("Zubeen Garg", "Papon", "Angarag Papon Mahanta", "Neel Akash", "Jonkey Borthakur", "Simanta Shekhar", "Kalpana Patowary", "Zubeen"),
    )

    fun artistsFor(languages: Collection<String>): List<String> {
        // Round-robin so every chosen language shows up early in the grid.
        val lists = languages.map { artistsByLanguage[it].orEmpty() }
        val out = LinkedHashSet<String>()
        var i = 0
        while (out.size < 400 && lists.any { i < it.size }) {
            lists.forEach { l -> l.getOrNull(i)?.let { out += it } }
            i++
        }
        return out.toList()
    }

    /** Search keyword for a language, e.g. "Hindi" -> "hindi". */
    fun q(language: String) = language.lowercase()
}
