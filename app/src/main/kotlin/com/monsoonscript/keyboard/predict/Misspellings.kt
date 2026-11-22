package com.monsoonscript.keyboard.predict

/**
 * Well-known English misspellings and the word each one means (L8, D69). The word lists contain such misspellings at a moderate
 * frequency next to real rare words, so no frequency rule can tell them apart; this short hand-made list can, which is why
 * [Corrector] auto-fixes these with full confidence and everything else only on request. Only plain words that are nothing else in
 * English: no real word, no slang (`thx`), no word that Banglish also uses. The [Corrector] still checks that the fix is in the list.
 * Format: "typo fix" pairs, lowercase, separated by spaces.
 */
object Misspellings {
    private const val PAIRS = """
        teh the  adn and  hte the  taht that  thier their  wiht with  waht what  whta what  whihc which  wich which
        becuase because  becasue because  becuse because  beacuse because  definately definitely  definatly definitely
        definitly definitely  definate definite  seperate separate  seperately separately  occured occurred
        occurence occurrence  occurance occurrence  recieve receive  recieved received  recive receive  reciept receipt
        wierd weird  freind friend  freinds friends  beleive believe  belive believe  beleived believed  beleif belief
        acheive achieve  acheived achieved  accomodate accommodate  accomodation accommodation  adress address
        adresses addresses  agressive aggressive  apparantly apparently  apparant apparent  arguement argument
        basicly basically  begining beginning  buisness business  bussiness business  calender calendar
        cemetary cemetery  collegue colleague  comming coming  commited committed
        completly completely  concious conscious  curiousity curiosity  dissapear disappear  dissapoint disappoint
        embarass embarrass  enviroment environment  existance existence  experiance experience  explaination explanation
        familar familiar  finaly finally  foriegn foreign  fourty forty  futher further  gaurd guard
        goverment government  grammer grammar  gratefull grateful  happend happened  harrass harass
        immediatly immediately  independant independent  interupt interrupt  knowlege knowledge  liason liaison
        libary library  maintainance maintenance  millenium millennium  neccessary necessary
        necesary necessary  noticable noticeable  occassion occasion  oppurtunity opportunity  orignal original
        paralell parallel  persistant persistent  posession possession  prefered preferred  privelege privilege
        probaly probably  professer professor  pronounciation pronunciation  publically publicly  realy really
        recomend recommend  refered referred  relevent relevant  religous religious  remeber remember
        resistence resistance  responsability responsibility  restaraunt restaurant  rythm rhythm  scedule schedule
        sentance sentence  succesful successful  succesfully successfully  sucess success  suprise surprise
        tommorow tomorrow  tomorow tomorrow  tommorrow tomorrow  tomrrow tomorrow  tounge tongue  truely truly
        untill until  usualy usually  vegtable vegetable  writting writing  yeild yield
        cna can  jsut just  abotu about  aobut about  somthing something  somethign something  alwasy always
        allways always  pelase please  plese please  pleaes please  wnat want  ahve have  hvae have  yuo you
        yoru your  yuor your  liek like  mroe more  wokr work  wrok work  knwo know  konw know  tiem time
        wnet went  wlil will  woudl would  shoudl should  coudl could  frist first  peice piece  peopel people
        poeple people  pepole people  diffrent different  differnt different  diffirent different
        intresting interesting  beautifull beautiful  beatiful beautiful  beutiful beautiful  togehter together
        togther together  wensday wednesday  wendsday wednesday  febuary february  wehn when  hwo how
    """

    private val MAP: Map<String, String> by lazy {
        val t = PAIRS.trim().split(Regex("\\s+"))
        val m = HashMap<String, String>()
        var i = 0
        while (i + 1 < t.size) {
            m[t[i]] = t[i + 1]
            i += 2
        }
        m
    }

    /** The word meant by the misspelling [key] (lowercase) or null. */
    fun fix(key: String): String? = MAP[key]

    /** All pairs, for the tests. */
    internal fun all(): Map<String, String> = MAP
}
