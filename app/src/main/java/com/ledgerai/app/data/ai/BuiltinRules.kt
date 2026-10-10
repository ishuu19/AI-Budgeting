package com.ledgerai.app.data.ai

/**
 * Small built-in phrase tables used when no asset lexicon is loaded, and alongside it. The big lists live in
 * the tsv files in `assets/rules`; these only keep the parser useful with nothing loaded.
 */
internal object BuiltinRules {

    private fun rowsOf(spec: String): List<List<String>> =
        spec.trimIndent().lines().filter { it.isNotBlank() }.flatMap { line ->
            val head = line.substringBefore(':').trim()
            line.substringAfter(':').split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { listOf(it, head) }
        }

    private fun merchants(spec: String): List<List<String>> =
        spec.trimIndent().lines().filter { it.isNotBlank() }.flatMap { line ->
            val parts = line.split('|').map { it.trim() }
            val cat = parts[0]
            parts.drop(1).filter { it.isNotEmpty() }.map { entry ->
                val alias = entry.substringBefore('=').trim()
                val canon = if ('=' in entry) entry.substringAfter('=').trim() else alias.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
                listOf(alias, cat, canon)
            }
        }

    private fun pairs(spec: String): List<List<String>> =
        spec.trimIndent().lines().filter { it.isNotBlank() }.map { line -> line.split('=').map { it.trim() } }

    private fun words(spec: String): List<List<String>> =
        spec.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.map { listOf(it) }

    val keywords: List<List<String>> = rowsOf(
        """
        FOOD: food, lunch, dinner, breakfast, brunch, snack, snacks, coffee, tea, restaurant, cafe, grocery, groceries, meal, takeaway, takeout, pizza, burger, biryani, sandwich, juice, ice cream, bakery, fruit, fruits, vegetables, veggies, eat out, eating out, drinks, supper, dessert, noodles, canteen, tiffin
        TRANSPORT: taxi, cab, bus, train, metro, fare, fuel, petrol, gas, diesel, parking, toll, rickshaw, cng, ride, transport, commute, ticket, flight, airfare, bike, scooter, ferry, tram
        SUBSCRIPTIONS: subscription, subscriptions, netflix, spotify, hulu, disney, apple tv, prime, youtube premium, icloud, chatgpt, membership
        ENTERTAINMENT: movie, movies, game, games, concert, cinema, theatre, theater, entertainment, party, bar, club, bowling, karaoke, show, festival
        SHOPPING: shopping, clothes, clothing, shoes, shirt, shirts, dress, jacket, bag, store, mall, gift, gifts, electronics, furniture, jewelry, watch, phone case, perfume
        HEALTH: doctor, pharmacy, gym, health, medicine, medicines, hospital, dentist, clinic, therapy, checkup, vitamins, prescription, lab test, surgery, glasses
        UTILITIES: electric, electricity, water, internet, utility, utilities, wifi, gas bill, phone bill, mobile bill, recharge, broadband, sewer, trash, mobile data, top up, topup
        RENT: rent, mortgage, housing, landlord, house rent, flat rent, apartment
        EDUCATION: tuition, school, college, university, course, courses, textbook, textbooks, books, book, fees, exam fee, stationery, coaching, class fee, library
        SALARY: salary, paycheck, paycheque, wages, wage, payroll, stipend, pension
        FREELANCE: freelance, gig, client, commission, consulting, side hustle
        """
    )

    val merchantRows: List<List<String>> = merchants(
        """
        FOOD | starbucks | mcdonald's=McDonald's | mcdonalds=McDonald's | kfc=KFC | subway | dominos=Domino's | domino's=Domino's | pizza hut | burger king | foodpanda | uber eats | doordash | grubhub | chipotle | dunkin | tim hortons | wendys=Wendy's | taco bell | panera | nandos=Nando's | swiggy | zomato | hungrynaki | shwapno | chaldal
        TRANSPORT | uber | lyft | pathao | shell | chevron | exxon | careem | obhai
        SHOPPING | amazon | walmart | target | costco | ikea | ebay | daraz | aliexpress | best buy | home depot | zara | h&m=H&M | nike | adidas | apple | flipkart | etsy
        SUBSCRIPTIONS | netflix | spotify | hulu | disney plus=Disney+ | youtube premium=YouTube Premium | hbo | prime video=Prime Video | apple music | icloud | chatgpt | openai | adobe | dropbox | notion | github
        UTILITIES | comcast | verizon | at&t=AT&T | t-mobile=T-Mobile | grameenphone | robi | banglalink | airtel
        HEALTH | cvs | walgreens | planet fitness | anytime fitness
        """
    )

    val taskVerbs: List<List<String>> = words(
        "call, phone, email, text, message, ring, buy, get, pick up, drop off, send, submit, finish, complete, pay, book, renew, " +
            "clean, wash, cook, study, read, write, review, prepare, plan, fix, visit, return, order, schedule, practice, workout, " +
            "exercise, meditate, water the plants, take out, pack, charge, update, check, follow up, print, upload, download, " +
            "reply, respond, register, sign up, apply, cancel, confirm, collect, deliver, iron, tidy, organize, research, " +
            "attend, meet, see, go to, go for, take, do, make, bring, feed, walk the dog, fill, wash the car"
    )

    val peopleRoles: List<List<String>> = words(
        "mom, mum, mother, dad, father, brother, sister, wife, husband, son, daughter, uncle, aunt, cousin, friend, boss, " +
            "neighbor, neighbour, roommate, landlord, colleague, coworker, partner, boyfriend, girlfriend, grandma, grandpa, " +
            "ammu, abbu, bhai, apu, khala, chacha, mama, dada, dadi, nana, nani"
    )

    val bills: List<List<String>> = words(
        "electricity, electric, water, gas, internet, wifi, broadband, phone, mobile, rent, insurance, tuition, mortgage, " +
            "netflix, spotify, gym, membership, cable, trash, loan, emi, credit card, car payment, subscription"
    )

    val jobTerms: List<List<String>> = words(
        "software engineer, developer, designer, analyst, manager, intern, internship, engineer, accountant, consultant, " +
            "product manager, data scientist, android developer, ios developer, frontend, backend, full stack, devops, " +
            "qa engineer, teacher, nurse, sales"
    )

    val bangla: List<List<String>> = pairs(
        """
        ajke = today
        ajk = today
        agamikal = tomorrow
        agami kal = tomorrow
        gotokal = yesterday
        gotokal = yesterday
        porshu = day after tomorrow
        khoroch = spent
        khorcho = spent
        kinlam = bought
        kinechi = bought
        dilam = paid
        diyechi = paid
        peyechi = received
        pelam = received
        bajar = grocery
        bazar = grocery
        beton = salary
        ghonta = hour
        ghanta = hour
        minit = min
        shokal = morning
        sokal = morning
        bikel = afternoon
        shondha = evening
        sondha = evening
        dhar nilam = borrowed
        dhar dilam = lent
        dhar nisi = borrowed
        dhar disi = lent
        mone koriye dao = remind me to
        mone koriye dio = remind me to
        """
    )

    val numberWords: Map<String, Double> = mapOf(
        "ek" to 1.0, "dui" to 2.0, "tin" to 3.0, "panch" to 5.0, "choy" to 6.0, "noy" to 9.0, "dosh" to 10.0,
        "bish" to 20.0, "tirish" to 30.0, "challish" to 40.0, "ponchash" to 50.0,
    )

    val lexicon: RuleLexicon by lazy {
        RuleLexicon(
            mapOf(
                RuleLexicon.KEYWORDS to keywords,
                RuleLexicon.MERCHANTS to merchantRows,
                RuleLexicon.TASK_VERBS to taskVerbs,
                RuleLexicon.PEOPLE to peopleRoles,
                RuleLexicon.BILLS to bills,
                RuleLexicon.JOB_TERMS to jobTerms,
                RuleLexicon.BANGLA to bangla,
            )
        )
    }
}
