/**
 * Standard administrative districts of Sri Lanka with unique 3-letter codes,
 * postal codes, province groupings, and postal sub-zones/towns.
 */

export interface SubZoneInfo {
  code: string;
  name: string;
  postalCode?: string;
}

export interface DistrictInfo {
  code: string;
  name: string;
  province: string;
  postalCode: string;
  defaultBranchName: string;
  zones: SubZoneInfo[];
}

export const SRI_LANKA_DISTRICTS: DistrictInfo[] = [
  // Western Province
  {
    code: "CMB",
    name: "Colombo",
    province: "Western",
    postalCode: "00100",
    defaultBranchName: "Colombo Branch",
    zones: [
      { code: "CMB-FRT", name: "Colombo 01 - Fort", postalCode: "00100" },
      { code: "CMB-SLI", name: "Colombo 02 - Slave Island", postalCode: "00200" },
      { code: "CMB-KLP", name: "Colombo 03 - Kollupitiya", postalCode: "00300" },
      { code: "CMB-BPT", name: "Colombo 04 - Bambalapitiya", postalCode: "00400" },
      { code: "CMB-HVT", name: "Colombo 05 - Havelock Town", postalCode: "00500" },
      { code: "CMB-WEL", name: "Colombo 06 - Wellawatte", postalCode: "00600" },
      { code: "CMB-CNG", name: "Colombo 07 - Cinnamon Gardens", postalCode: "00700" },
      { code: "CMB-BOR", name: "Colombo 08 - Borella", postalCode: "00800" },
      { code: "CMB-DMT", name: "Colombo 09 - Dematagoda", postalCode: "00900" },
      { code: "CMB-MRD", name: "Colombo 10 - Maradana", postalCode: "01000" },
      { code: "CMB-PET", name: "Colombo 11 - Pettah", postalCode: "01100" },
      { code: "CMB-HLT", name: "Colombo 12 - Hultsdorf", postalCode: "01200" },
      { code: "CMB-KTH", name: "Colombo 13 - Kotahena", postalCode: "01300" },
      { code: "CMB-GRP", name: "Colombo 14 - Grandpass", postalCode: "01400" },
      { code: "CMB-MTW", name: "Colombo 15 - Mutwal", postalCode: "01500" },
      { code: "CMB-DHW", name: "Dehiwala", postalCode: "10350" },
      { code: "CMB-MTL", name: "Mount Lavinia", postalCode: "10370" },
      { code: "CMB-MRT", name: "Moratuwa", postalCode: "10400" },
      { code: "CMB-NGD", name: "Nugegoda", postalCode: "10250" },
      { code: "CMB-MHG", name: "Maharagama", postalCode: "10280" },
      { code: "CMB-KTW", name: "Kottawa", postalCode: "10230" },
      { code: "CMB-BTR", name: "Battaramulla", postalCode: "10120" },
      { code: "CMB-RJG", name: "Rajagiriya", postalCode: "10107" },
      { code: "CMB-MLB", name: "Malabe", postalCode: "10115" },
      { code: "CMB-KDW", name: "Kaduwela", postalCode: "10640" },
      { code: "CMB-HMG", name: "Homagama", postalCode: "10200" },
      { code: "CMB-PLN", name: "Piliyandala", postalCode: "10300" },
      { code: "CMB-KSB", name: "Kesbewa", postalCode: "10305" },
      { code: "CMB-HNW", name: "Hanwella", postalCode: "10656" },
      { code: "CMB-AVS", name: "Avissawella", postalCode: "10700" },
    ],
  },
  {
    code: "GAM",
    name: "Gampaha",
    province: "Western",
    postalCode: "11000",
    defaultBranchName: "Gampaha Branch",
    zones: [
      { code: "GAM-GMP", name: "Gampaha Town", postalCode: "11000" },
      { code: "GAM-NEG", name: "Negombo", postalCode: "11500" },
      { code: "GAM-KEL", name: "Kelaniya", postalCode: "11600" },
      { code: "GAM-WAT", name: "Wattala", postalCode: "11300" },
      { code: "GAM-JAE", name: "Ja-Ela", postalCode: "11350" },
      { code: "GAM-KAN", name: "Kandana", postalCode: "11320" },
      { code: "GAM-KAD", name: "Kadawatha", postalCode: "11850" },
      { code: "GAM-KIR", name: "Kiribathgoda", postalCode: "11600" },
      { code: "GAM-MIN", name: "Minuwangoda", postalCode: "11550" },
      { code: "GAM-MIR", name: "Mirigama", postalCode: "11200" },
      { code: "GAM-NIT", name: "Nittambuwa", postalCode: "11880" },
      { code: "GAM-BIY", name: "Biyagama", postalCode: "11650" },
      { code: "GAM-RAG", name: "Ragama", postalCode: "11010" },
      { code: "GAM-DIV", name: "Divulapitiya", postalCode: "11250" },
      { code: "GAM-KAT", name: "Katunayake", postalCode: "11450" },
    ],
  },
  {
    code: "KAL",
    name: "Kalutara",
    province: "Western",
    postalCode: "12000",
    defaultBranchName: "Kalutara Branch",
    zones: [
      { code: "KAL-KAL", name: "Kalutara Town", postalCode: "12000" },
      { code: "KAL-PAN", name: "Panadura", postalCode: "12500" },
      { code: "KAL-HOR", name: "Horana", postalCode: "12400" },
      { code: "KAL-BER", name: "Beruwala", postalCode: "12070" },
      { code: "KAL-ALU", name: "Aluthgama", postalCode: "12080" },
      { code: "KAL-BAN", name: "Bandaragama", postalCode: "12530" },
      { code: "KAL-MAT", name: "Matugama", postalCode: "12100" },
      { code: "KAL-WAD", name: "Wadduwa", postalCode: "12560" },
      { code: "KAL-AGA", name: "Agalawatta", postalCode: "12200" },
      { code: "KAL-BUL", name: "Bulathsinhala", postalCode: "12300" },
    ],
  },

  // Central Province
  {
    code: "KDY",
    name: "Kandy",
    province: "Central",
    postalCode: "20000",
    defaultBranchName: "Kandy Branch",
    zones: [
      { code: "KDY-KND", name: "Kandy City", postalCode: "20000" },
      { code: "KDY-PER", name: "Peradeniya", postalCode: "20400" },
      { code: "KDY-KAT", name: "Katugastota", postalCode: "20800" },
      { code: "KDY-AMP", name: "Ampitiya", postalCode: "20160" },
      { code: "KDY-DIG", name: "Digana", postalCode: "20180" },
      { code: "KDY-KUN", name: "Kundasale", postalCode: "20168" },
      { code: "KDY-GAM", name: "Gampola", postalCode: "20500" },
      { code: "KDY-NAW", name: "Nawalapitiya", postalCode: "20650" },
      { code: "KDY-AKU", name: "Akurana", postalCode: "20850" },
      { code: "KDY-PIL", name: "Pilimathalawa", postalCode: "20450" },
      { code: "KDY-KAD", name: "Kadugannawa", postalCode: "20300" },
      { code: "KDY-WAT", name: "Wattegama", postalCode: "20810" },
      { code: "KDY-TEL", name: "Teldeniya", postalCode: "20900" },
    ],
  },
  {
    code: "MTL",
    name: "Matale",
    province: "Central",
    postalCode: "21000",
    defaultBranchName: "Matale Branch",
    zones: [
      { code: "MTL-MAT", name: "Matale Town", postalCode: "21000" },
      { code: "MTL-DAM", name: "Dambulla", postalCode: "21100" },
      { code: "MTL-GAL", name: "Galewela", postalCode: "21200" },
      { code: "MTL-UKU", name: "Ukuwela", postalCode: "21300" },
      { code: "MTL-RAT", name: "Rattota", postalCode: "21400" },
      { code: "MTL-SIG", name: "Sigiriya", postalCode: "21120" },
      { code: "MTL-NAU", name: "Naula", postalCode: "21210" },
    ],
  },
  {
    code: "NUE",
    name: "Nuwara Eliya",
    province: "Central",
    postalCode: "22200",
    defaultBranchName: "Nuwara Eliya Branch",
    zones: [
      { code: "NUE-NUW", name: "Nuwara Eliya Town", postalCode: "22200" },
      { code: "NUE-HAT", name: "Hatton", postalCode: "22000" },
      { code: "NUE-TAL", name: "Talawakele", postalCode: "22100" },
      { code: "NUE-GIN", name: "Ginigathena", postalCode: "22080" },
      { code: "NUE-MAS", name: "Maskeliya", postalCode: "22070" },
      { code: "NUE-KOT", name: "Kotagala", postalCode: "22080" },
      { code: "NUE-RAG", name: "Ragala", postalCode: "22220" },
      { code: "NUE-WAL", name: "Walapane", postalCode: "22270" },
    ],
  },

  // Southern Province
  {
    code: "GAL",
    name: "Galle",
    province: "Southern",
    postalCode: "80000",
    defaultBranchName: "Galle Branch",
    zones: [
      { code: "GAL-GAL", name: "Galle Fort / Town", postalCode: "80000" },
      { code: "GAL-KAR", name: "Karapitiya", postalCode: "80000" },
      { code: "GAL-HIK", name: "Hikkaduwa", postalCode: "80240" },
      { code: "GAL-AMB", name: "Ambalangoda", postalCode: "80300" },
      { code: "GAL-BAD", name: "Baddegama", postalCode: "80200" },
      { code: "GAL-KOG", name: "Koggala", postalCode: "80130" },
      { code: "GAL-AHA", name: "Ahangama", postalCode: "80650" },
      { code: "GAL-BEN", name: "Bentota", postalCode: "80500" },
      { code: "GAL-ELP", name: "Elpitiya", postalCode: "80400" },
      { code: "GAL-BAL", name: "Balapitiya", postalCode: "80550" },
      { code: "GAL-NEL", name: "Neluwa", postalCode: "80082" },
    ],
  },
  {
    code: "MAT",
    name: "Matara",
    province: "Southern",
    postalCode: "81000",
    defaultBranchName: "Matara Branch",
    zones: [
      { code: "MAT-MAT", name: "Matara Town", postalCode: "81000" },
      { code: "MAT-WEL", name: "Weligama", postalCode: "81700" },
      { code: "MAT-MIR", name: "Mirissa", postalCode: "81740" },
      { code: "MAT-DIK", name: "Dikwella", postalCode: "81100" },
      { code: "MAT-AKU", name: "Akuressa", postalCode: "81300" },
      { code: "MAT-DEN", name: "Deniyaya", postalCode: "81500" },
      { code: "MAT-HAK", name: "Hakmana", postalCode: "81330" },
      { code: "MAT-KAM", name: "Kamburupitiya", postalCode: "81050" },
      { code: "MAT-GAN", name: "Gandara", postalCode: "81170" },
    ],
  },
  {
    code: "HAM",
    name: "Hambantota",
    province: "Southern",
    postalCode: "82000",
    defaultBranchName: "Hambantota Branch",
    zones: [
      { code: "HAM-HAM", name: "Hambantota Town", postalCode: "82000" },
      { code: "HAM-TAN", name: "Tangalle", postalCode: "82200" },
      { code: "HAM-BEL", name: "Beliatta", postalCode: "82400" },
      { code: "HAM-AMB", name: "Ambalantota", postalCode: "82100" },
      { code: "HAM-TIS", name: "Tissamaharama", postalCode: "82600" },
      { code: "HAM-KAT", name: "Kataragama", postalCode: "91400" },
      { code: "HAM-WAL", name: "Walasmulla", postalCode: "82450" },
      { code: "HAM-WEE", name: "Weeraketiya", postalCode: "82240" },
    ],
  },

  // Northern Province
  {
    code: "JAF",
    name: "Jaffna",
    province: "Northern",
    postalCode: "40000",
    defaultBranchName: "Jaffna Branch",
    zones: [
      { code: "JAF-JAF", name: "Jaffna Town", postalCode: "40000" },
      { code: "JAF-NAL", name: "Nallur", postalCode: "40000" },
      { code: "JAF-CHV", name: "Chavakachcheri", postalCode: "40500" },
      { code: "JAF-PTP", name: "Point Pedro", postalCode: "40600" },
      { code: "JAF-CHU", name: "Chunnakam", postalCode: "40000" },
      { code: "JAF-VVT", name: "Valvettithurai", postalCode: "40630" },
      { code: "JAF-KAR", name: "Karainagar", postalCode: "40000" },
      { code: "JAF-TEL", name: "Tellippalai", postalCode: "40000" },
      { code: "JAF-KOP", name: "Kopay", postalCode: "40000" },
    ],
  },
  {
    code: "KIL",
    name: "Kilinochchi",
    province: "Northern",
    postalCode: "44000",
    defaultBranchName: "Kilinochchi Branch",
    zones: [
      { code: "KIL-KIL", name: "Kilinochchi Town", postalCode: "44000" },
      { code: "KIL-PAR", name: "Paranthan", postalCode: "44000" },
      { code: "KIL-PAL", name: "Pallai", postalCode: "44000" },
      { code: "KIL-POO", name: "Poonakary", postalCode: "44000" },
    ],
  },
  {
    code: "MAN",
    name: "Mannar",
    province: "Northern",
    postalCode: "41000",
    defaultBranchName: "Mannar Branch",
    zones: [
      { code: "MAN-MAN", name: "Mannar Town", postalCode: "41000" },
      { code: "MAN-MUR", name: "Murunkan", postalCode: "41000" },
      { code: "MAN-PES", name: "Pesalai", postalCode: "41000" },
      { code: "MAN-MAD", name: "Madhu", postalCode: "41000" },
      { code: "MAN-TAL", name: "Talaimannar", postalCode: "41000" },
    ],
  },
  {
    code: "VAV",
    name: "Vavuniya",
    province: "Northern",
    postalCode: "43000",
    defaultBranchName: "Vavuniya Branch",
    zones: [
      { code: "VAV-VAV", name: "Vavuniya Town", postalCode: "43000" },
      { code: "VAV-CHE", name: "Cheddikulam", postalCode: "43000" },
      { code: "VAV-NED", name: "Nedunkeni", postalCode: "43000" },
      { code: "VAV-OMA", name: "Omanthai", postalCode: "43000" },
    ],
  },
  {
    code: "MUL",
    name: "Mullaitivu",
    province: "Northern",
    postalCode: "42000",
    defaultBranchName: "Mullaitivu Branch",
    zones: [
      { code: "MUL-MUL", name: "Mullaitivu Town", postalCode: "42000" },
      { code: "MUL-PKY", name: "Puthukkudiyiruppu", postalCode: "42000" },
      { code: "MUL-ODD", name: "Oddusuddan", postalCode: "42000" },
      { code: "MUL-MAN", name: "Mankulam", postalCode: "42000" },
    ],
  },

  // Eastern Province
  {
    code: "BAT",
    name: "Batticaloa",
    province: "Eastern",
    postalCode: "30000",
    defaultBranchName: "Batticaloa Branch",
    zones: [
      { code: "BAT-BAT", name: "Batticaloa Town", postalCode: "30000" },
      { code: "BAT-KAT", name: "Kattankudy", postalCode: "30100" },
      { code: "BAT-ERA", name: "Eravur", postalCode: "30300" },
      { code: "BAT-VAL", name: "Valachchenai", postalCode: "30400" },
      { code: "BAT-KAL", name: "Kaluwanchikudy", postalCode: "30200" },
      { code: "BAT-CHE", name: "Chenkalady", postalCode: "30350" },
    ],
  },
  {
    code: "AMP",
    name: "Ampara",
    province: "Eastern",
    postalCode: "32000",
    defaultBranchName: "Ampara Branch",
    zones: [
      { code: "AMP-AMP", name: "Ampara Town", postalCode: "32000" },
      { code: "AMP-KAL", name: "Kalmunai", postalCode: "32300" },
      { code: "AMP-SAM", name: "Sammanthurai", postalCode: "32200" },
      { code: "AMP-AKK", name: "Akkaraipattu", postalCode: "32400" },
      { code: "AMP-POT", name: "Pottuvil / Arugam Bay", postalCode: "32500" },
      { code: "AMP-SAI", name: "Sainthamaruthu", postalCode: "32300" },
      { code: "AMP-DEH", name: "Dehiattakandiya", postalCode: "32150" },
    ],
  },
  {
    code: "TRI",
    name: "Trincomalee",
    province: "Eastern",
    postalCode: "31000",
    defaultBranchName: "Trincomalee Branch",
    zones: [
      { code: "TRI-TRI", name: "Trincomalee Town", postalCode: "31000" },
      { code: "TRI-KIN", name: "Kinniya", postalCode: "31100" },
      { code: "TRI-MUT", name: "Mutur", postalCode: "31200" },
      { code: "TRI-KAN", name: "Kantale", postalCode: "31300" },
      { code: "TRI-NIL", name: "Nilaveli", postalCode: "31010" },
    ],
  },

  // North Western Province
  {
    code: "KUR",
    name: "Kurunegala",
    province: "North Western",
    postalCode: "60000",
    defaultBranchName: "Kurunegala Branch",
    zones: [
      { code: "KUR-KUR", name: "Kurunegala Town", postalCode: "60000" },
      { code: "KUR-KUL", name: "Kuliyapitiya", postalCode: "60200" },
      { code: "KUR-NAR", name: "Narammala", postalCode: "60100" },
      { code: "KUR-WAR", name: "Wariyapola", postalCode: "60400" },
      { code: "KUR-PAN", name: "Pannala", postalCode: "60160" },
      { code: "KUR-POL", name: "Polgahawela", postalCode: "60300" },
      { code: "KUR-ALA", name: "Alawwa", postalCode: "60280" },
      { code: "KUR-IBB", name: "Ibbagamuwa", postalCode: "60500" },
      { code: "KUR-GIR", name: "Giriulla", postalCode: "60140" },
      { code: "KUR-MAW", name: "Mawathagama", postalCode: "60060" },
    ],
  },
  {
    code: "PUT",
    name: "Puttalam",
    province: "North Western",
    postalCode: "61000",
    defaultBranchName: "Puttalam Branch",
    zones: [
      { code: "PUT-PUT", name: "Puttalam Town", postalCode: "61300" },
      { code: "PUT-CHI", name: "Chilaw", postalCode: "61000" },
      { code: "PUT-WEN", name: "Wennappuwa", postalCode: "61170" },
      { code: "PUT-MAR", name: "Marawila", postalCode: "61160" },
      { code: "PUT-DAN", name: "Dankotuwa", postalCode: "61180" },
      { code: "PUT-ANA", name: "Anamaduwa", postalCode: "61500" },
      { code: "PUT-KAL", name: "Kalpitiya", postalCode: "61280" },
      { code: "PUT-NAT", name: "Nattandiya", postalCode: "61140" },
    ],
  },

  // North Central Province
  {
    code: "ANU",
    name: "Anuradhapura",
    province: "North Central",
    postalCode: "50000",
    defaultBranchName: "Anuradhapura Branch",
    zones: [
      { code: "ANU-ANU", name: "Anuradhapura Town", postalCode: "50000" },
      { code: "ANU-KEK", name: "Kekirawa", postalCode: "50100" },
      { code: "ANU-MED", name: "Medawachchiya", postalCode: "50500" },
      { code: "ANU-TAM", name: "Tambuttegama", postalCode: "50140" },
      { code: "ANU-EPP", name: "Eppawala", postalCode: "50180" },
      { code: "ANU-MIH", name: "Mihintale", postalCode: "50300" },
      { code: "ANU-GAL", name: "Galenbindunuwewa", postalCode: "50340" },
      { code: "ANU-NOC", name: "Nochchiyagama", postalCode: "50200" },
      { code: "ANU-HAB", name: "Habarana", postalCode: "50150" },
    ],
  },
  {
    code: "POL",
    name: "Polonnaruwa",
    province: "North Central",
    postalCode: "51000",
    defaultBranchName: "Polonnaruwa Branch",
    zones: [
      { code: "POL-POL", name: "Polonnaruwa Town", postalCode: "51000" },
      { code: "POL-KAD", name: "Kaduruwela", postalCode: "51000" },
      { code: "POL-HIN", name: "Hingurakgoda", postalCode: "51400" },
      { code: "POL-MED", name: "Medirigiriya", postalCode: "51500" },
      { code: "POL-WEL", name: "Welikanda", postalCode: "51070" },
      { code: "POL-MIN", name: "Minneriya", postalCode: "51410" },
    ],
  },

  // Uva Province
  {
    code: "BAD",
    name: "Badulla",
    province: "Uva",
    postalCode: "90000",
    defaultBranchName: "Badulla Branch",
    zones: [
      { code: "BAD-BAD", name: "Badulla Town", postalCode: "90000" },
      { code: "BAD-BAN", name: "Bandarawela", postalCode: "90100" },
      { code: "BAD-ELL", name: "Ella", postalCode: "90090" },
      { code: "BAD-WEL", name: "Welimada", postalCode: "90200" },
      { code: "BAD-MAH", name: "Mahiyanganaya", postalCode: "90700" },
      { code: "BAD-HAL", name: "Hali-Ela", postalCode: "90060" },
      { code: "BAD-HAP", name: "Haputale", postalCode: "90160" },
      { code: "BAD-PAS", name: "Passara", postalCode: "90500" },
      { code: "BAD-DIY", name: "Diyatalawa", postalCode: "90150" },
    ],
  },
  {
    code: "MON",
    name: "Monaragala",
    province: "Uva",
    postalCode: "91000",
    defaultBranchName: "Monaragala Branch",
    zones: [
      { code: "MON-MON", name: "Monaragala Town", postalCode: "91000" },
      { code: "MON-WEL", name: "Wellawaya", postalCode: "91200" },
      { code: "MON-BUT", name: "Buttala", postalCode: "91100" },
      { code: "MON-BIB", name: "Bibile", postalCode: "91500" },
      { code: "MON-KAT", name: "Kataragama", postalCode: "91400" },
      { code: "MON-SIY", name: "Siyambalanduwa", postalCode: "91300" },
    ],
  },

  // Sabaragamuwa Province
  {
    code: "RAT",
    name: "Ratnapura",
    province: "Sabaragamuwa",
    postalCode: "70000",
    defaultBranchName: "Ratnapura Branch",
    zones: [
      { code: "RAT-RAT", name: "Ratnapura Town", postalCode: "70000" },
      { code: "RAT-EMB", name: "Embilipitiya", postalCode: "70200" },
      { code: "RAT-BAL", name: "Balangoda", postalCode: "70100" },
      { code: "RAT-PEL", name: "Pelmadulla", postalCode: "70070" },
      { code: "RAT-KUR", name: "Kuruwita", postalCode: "70500" },
      { code: "RAT-EHE", name: "Eheliyagoda", postalCode: "70600" },
      { code: "RAT-KAH", name: "Kahawatta", postalCode: "70150" },
      { code: "RAT-KAL", name: "Kalawana", postalCode: "70450" },
    ],
  },
  {
    code: "KEG",
    name: "Kegalle",
    province: "Sabaragamuwa",
    postalCode: "71000",
    defaultBranchName: "Kegalle Branch",
    zones: [
      { code: "KEG-KEG", name: "Kegalle Town", postalCode: "71000" },
      { code: "KEG-MAW", name: "Mawanella", postalCode: "71500" },
      { code: "KEG-WAR", name: "Warakapola", postalCode: "71600" },
      { code: "KEG-RUW", name: "Ruwanwella", postalCode: "71700" },
      { code: "KEG-YAT", name: "Yatiyantota", postalCode: "71720" },
      { code: "KEG-DEH", name: "Dehiowita", postalCode: "71400" },
      { code: "KEG-RAM", name: "Rambukkana", postalCode: "71100" },
      { code: "KEG-DER", name: "Deraniyagala", postalCode: "71430" },
    ],
  },
];

export function findDistrictByName(name: string): DistrictInfo | undefined {
  const normalized = name.trim().toLowerCase();
  return SRI_LANKA_DISTRICTS.find(
    (d) => d.name.toLowerCase() === normalized || d.code.toLowerCase() === normalized
  );
}

export function findDistrictByCode(code: string): DistrictInfo | undefined {
  const normalized = code.trim().toLowerCase();
  return SRI_LANKA_DISTRICTS.find(
    (d) => d.code.toLowerCase() === normalized || d.name.toLowerCase() === normalized
  );
}

export function getZonesForDistrict(districtNameOrCode: string): SubZoneInfo[] {
  const district =
    findDistrictByName(districtNameOrCode) || findDistrictByCode(districtNameOrCode);
  return district ? district.zones : [];
}

