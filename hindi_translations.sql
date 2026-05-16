-- 1. ADAPALENE
UPDATE drugs SET
  common_uses_simple_hi = 'मुहांसे (पिंपल) के इलाज के लिए। चेहरे पर लगाने वाली क्रीम।',
  standard_adult_dose_hi = 'रोज़ रात को एक बार पतली परत लगाएं',
  common_side_effects_hi = 'त्वचा में सूखापन, लालिमा, जलन, छिलना',
  contraindications_hi = 'गर्भावस्था में न लगाएं। खुले घाव या जलन पर न लगाएं।',
  alcohol_warning_hi = 'शराब के साथ कोई बड़ी समस्या नहीं, परंतु त्वचा सूखी हो सकती है।'
WHERE generic_name = 'adapalene';

-- 2. AMLODIPINE
UPDATE drugs SET
  common_uses_simple_hi = 'उच्च रक्तचाप (हाई बीपी) और सीने के दर्द (एनजाइना) के लिए।',
  standard_adult_dose_hi = 'रोज़ एक बार 5-10 मि.ग्रा.',
  common_side_effects_hi = 'टखनों में सूजन, सिरदर्द, चक्कर, चेहरे पर लाली',
  contraindications_hi = 'बहुत कम बीपी हो तो न लें। गंभीर हृदय रोग में डॉक्टर से पूछें।',
  alcohol_warning_hi = 'शराब से बचें — बीपी बहुत कम हो सकता है और चक्कर आ सकते हैं।'
WHERE generic_name = 'amlodipine';

-- 3. AMOXICILLIN
UPDATE drugs SET
  common_uses_simple_hi = 'बैक्टीरिया से होने वाले संक्रमण के लिए एंटीबायोटिक।',
  standard_adult_dose_hi = 'दिन में 3 बार 500 मि.ग्रा. — पूरा कोर्स लें',
  common_side_effects_hi = 'पेट खराब, दस्त, मतली, त्वचा पर रैश',
  contraindications_hi = 'पेनिसिलिन से एलर्जी हो तो न लें। डॉक्टर को बताएं।',
  alcohol_warning_hi = 'शराब से बचें — दवा कम असर कर सकती है।'
WHERE generic_name = 'amoxicillin';

-- 4. AMOXICILLIN-CLAVULANATE
UPDATE drugs SET
  common_uses_simple_hi = 'मज़बूत बैक्टीरिया संक्रमण के लिए एंटीबायोटिक संयोजन।',
  standard_adult_dose_hi = 'दिन में 2 बार 625 मि.ग्रा. — पूरा कोर्स लें',
  common_side_effects_hi = 'दस्त, पेट दर्द, मतली, रैश',
  contraindications_hi = 'पेनिसिलिन एलर्जी या लीवर की बीमारी में न लें।',
  alcohol_warning_hi = 'शराब से बचें — लीवर पर असर पड़ सकता है।'
WHERE generic_name = 'amoxicillin-clavulanate';

-- 5. ASPIRIN
UPDATE drugs SET
  common_uses_simple_hi = 'दर्द, बुखार, और हृदय रोग से बचाव के लिए।',
  standard_adult_dose_hi = 'दर्द: 300-600 मि.ग्रा. हर 4-6 घंटे। हृदय: रोज़ 75 मि.ग्रा.',
  common_side_effects_hi = 'पेट में जलन, अल्सर, खून बहना, मतली',
  contraindications_hi = '16 साल से कम उम्र, गर्भावस्था, अल्सर, या रक्तस्राव की बीमारी में न लें।',
  alcohol_warning_hi = 'शराब से बचें — पेट में खून बहने का बड़ा खतरा।'
WHERE generic_name = 'aspirin';

-- 6. ATORVASTATIN
UPDATE drugs SET
  common_uses_simple_hi = 'खून में कोलेस्ट्रॉल कम करने के लिए।',
  standard_adult_dose_hi = 'रोज़ रात को एक बार 10-40 मि.ग्रा.',
  common_side_effects_hi = 'मांसपेशियों में दर्द, सिरदर्द, पेट खराब',
  contraindications_hi = 'गर्भावस्था, स्तनपान, या लीवर की बीमारी में न लें।',
  alcohol_warning_hi = 'शराब सीमित करें — लीवर पर असर बढ़ सकता है।'
WHERE generic_name = 'atorvastatin';

-- 7. AZITHROMYCIN
UPDATE drugs SET
  common_uses_simple_hi = 'सांस, गले, और त्वचा के संक्रमण के लिए एंटीबायोटिक।',
  standard_adult_dose_hi = 'पहले दिन 500 मि.ग्रा., फिर 4 दिन 250 मि.ग्रा.',
  common_side_effects_hi = 'मतली, दस्त, पेट दर्द, सिरदर्द',
  contraindications_hi = 'मैक्रोलाइड एलर्जी, गंभीर लीवर रोग, या हृदय की लय की समस्या में न लें।',
  alcohol_warning_hi = 'शराब से बचें — मतली और लीवर पर असर बढ़ता है।'
WHERE generic_name = 'azithromycin';

-- 8. CETIRIZINE
UPDATE drugs SET
  common_uses_simple_hi = 'एलर्जी, छींक, खुजली, और पित्ती के लिए।',
  standard_adult_dose_hi = 'रोज़ एक बार 10 मि.ग्रा.',
  common_side_effects_hi = 'नींद आना, मुंह सूखना, थकान, सिरदर्द',
  contraindications_hi = 'गंभीर किडनी रोग में डॉक्टर से पूछें।',
  alcohol_warning_hi = 'शराब से बचें — नींद बहुत ज़्यादा आ सकती है।'
WHERE generic_name = 'cetirizine';

-- 9. CHOLECALCIFEROL
UPDATE drugs SET
  common_uses_simple_hi = 'विटामिन डी3 की कमी पूरा करने के लिए। हड्डियों की मज़बूती।',
  standard_adult_dose_hi = 'रोज़ 1000-2000 IU या डॉक्टर के बताए अनुसार',
  common_side_effects_hi = 'सामान्य मात्रा में दुष्प्रभाव कम। ज़्यादा लेने पर मतली, कमज़ोरी।',
  contraindications_hi = 'खून में कैल्शियम ज़्यादा हो या किडनी पथरी हो तो डॉक्टर से पूछें।',
  alcohol_warning_hi = 'शराब के साथ कोई बड़ी समस्या नहीं।'
WHERE generic_name = 'cholecalciferol';

-- 10. CIPROFLOXACIN
UPDATE drugs SET
  common_uses_simple_hi = 'पेशाब, पेट, और सांस के संक्रमण के लिए मज़बूत एंटीबायोटिक।',
  standard_adult_dose_hi = 'दिन में 2 बार 500 मि.ग्रा. — पूरा कोर्स लें',
  common_side_effects_hi = 'मतली, दस्त, सिरदर्द, चक्कर, जोड़ों में दर्द',
  contraindications_hi = 'गर्भावस्था, 18 साल से कम उम्र, या टेंडन की समस्या में न लें।',
  alcohol_warning_hi = 'शराब से बचें — चक्कर और लीवर पर असर बढ़ता है।'
WHERE generic_name = 'ciprofloxacin';

-- 11. CLINDAMYCIN
UPDATE drugs SET
  common_uses_simple_hi = 'गंभीर बैक्टीरिया संक्रमण और दांत के फोड़े के लिए।',
  standard_adult_dose_hi = 'दिन में 3-4 बार 150-300 मि.ग्रा.',
  common_side_effects_hi = 'दस्त (कभी गंभीर), मतली, पेट दर्द, रैश',
  contraindications_hi = 'पहले दस्त की गंभीर समस्या (कोलाइटिस) हो तो न लें।',
  alcohol_warning_hi = 'शराब से बचें — पेट की समस्या बढ़ सकती है।'
WHERE generic_name = 'clindamycin';

-- 12. CLOTRIMAZOLE
UPDATE drugs SET
  common_uses_simple_hi = 'फंगल संक्रमण (दाद, खाज, खुजली) के लिए लगाने वाली क्रीम।',
  standard_adult_dose_hi = 'दिन में 2-3 बार पतली परत लगाएं, 2-4 हफ्ते तक',
  common_side_effects_hi = 'त्वचा में जलन, लालिमा, खुजली (लगाने की जगह पर)',
  contraindications_hi = 'क्लोट्रिमेज़ोल से एलर्जी हो तो न लगाएं।',
  alcohol_warning_hi = 'शराब के साथ कोई समस्या नहीं — यह केवल त्वचा पर लगती है।'
WHERE generic_name = 'clotrimazole';

-- 13. DICLOFENAC
UPDATE drugs SET
  common_uses_simple_hi = 'दर्द, सूजन, और गठिया के लिए।',
  standard_adult_dose_hi = 'दिन में 2-3 बार 50 मि.ग्रा. (खाने के बाद)',
  common_side_effects_hi = 'पेट दर्द, अल्सर, मतली, सिरदर्द, बीपी बढ़ना',
  contraindications_hi = 'अल्सर, हृदय रोग, किडनी रोग, या गर्भावस्था (आखिरी 3 महीने) में न लें।',
  alcohol_warning_hi = 'शराब से बचें — पेट में खून बहने का बड़ा खतरा।'
WHERE generic_name = 'diclofenac';

-- 14. ESOMEPRAZOLE
UPDATE drugs SET
  common_uses_simple_hi = 'पेट में अम्ल (एसिडिटी), गैस्ट्राइटिस, और अल्सर के लिए।',
  standard_adult_dose_hi = 'रोज़ एक बार 20-40 मि.ग्रा., खाने से पहले',
  common_side_effects_hi = 'सिरदर्द, दस्त, मतली, पेट दर्द',
  contraindications_hi = 'एसोमेप्राज़ोल से एलर्जी हो तो न लें। लंबे समय तक उपयोग से पहले डॉक्टर से पूछें।',
  alcohol_warning_hi = 'शराब सीमित करें — एसिडिटी बढ़ सकती है।'
WHERE generic_name = 'esomeprazole';

-- 15. FERROUS-SULFATE
UPDATE drugs SET
  common_uses_simple_hi = 'खून की कमी (एनीमिया) में आयरन की पूर्ति के लिए।',
  standard_adult_dose_hi = 'रोज़ 1-2 गोली, खाने के बाद',
  common_side_effects_hi = 'काला मल, कब्ज़, पेट दर्द, मतली',
  contraindications_hi = 'शरीर में आयरन ज़्यादा हो (हीमोक्रोमैटोसिस) तो न लें।',
  alcohol_warning_hi = 'शराब से बचें — पेट की समस्या बढ़ सकती है।'
WHERE generic_name = 'ferrous-sulfate';

-- 16. GLIMEPIRIDE
UPDATE drugs SET
  common_uses_simple_hi = 'टाइप 2 डायबिटीज़ (शुगर) के इलाज के लिए।',
  standard_adult_dose_hi = 'रोज़ एक बार 1-4 मि.ग्रा., नाश्ते से पहले',
  common_side_effects_hi = 'शुगर बहुत कम होना (हाइपोग्लाइसीमिया), वज़न बढ़ना, मतली',
  contraindications_hi = 'टाइप 1 डायबिटीज़, गर्भावस्था, या सल्फा एलर्जी में न लें।',
  alcohol_warning_hi = 'शराब से बचें — शुगर खतरनाक रूप से कम हो सकती है।'
WHERE generic_name = 'glimepiride';

-- 17. IBUPROFEN
UPDATE drugs SET
  common_uses_simple_hi = 'दर्द, बुखार, और सूजन के लिए।',
  standard_adult_dose_hi = 'हर 6-8 घंटे 200-400 मि.ग्रा., खाने के बाद',
  common_side_effects_hi = 'पेट दर्द, अल्सर, मतली, सिरदर्द, चक्कर',
  contraindications_hi = 'अल्सर, हृदय रोग, किडनी रोग, या गर्भावस्था (आखिरी 3 महीने) में न लें।',
  alcohol_warning_hi = 'शराब से बचें — पेट में खून बहने का खतरा।'
WHERE generic_name = 'ibuprofen';

-- 18. LEVOCETIRIZINE
UPDATE drugs SET
  common_uses_simple_hi = 'एलर्जी, छींक, और खुजली के लिए (कम नींद वाली)।',
  standard_adult_dose_hi = 'रोज़ एक बार 5 मि.ग्रा., शाम को',
  common_side_effects_hi = 'हल्की नींद, मुंह सूखना, थकान, सिरदर्द',
  contraindications_hi = 'गंभीर किडनी रोग में डॉक्टर से पूछें।',
  alcohol_warning_hi = 'शराब से बचें — नींद बढ़ सकती है।'
WHERE generic_name = 'levocetirizine';

-- 19. LOSARTAN
UPDATE drugs SET
  common_uses_simple_hi = 'उच्च रक्तचाप (हाई बीपी) और किडनी की रक्षा के लिए।',
  standard_adult_dose_hi = 'रोज़ एक बार 50-100 मि.ग्रा.',
  common_side_effects_hi = 'चक्कर, थकान, खांसी, पोटाशियम बढ़ना',
  contraindications_hi = 'गर्भावस्था में बिल्कुल न लें। डायबिटीज़ + किडनी रोग में डॉक्टर से पूछें।',
  alcohol_warning_hi = 'शराब से बचें — बीपी बहुत कम हो सकता है।'
WHERE generic_name = 'losartan';

-- 20. METFORMIN
UPDATE drugs SET
  common_uses_simple_hi = 'टाइप 2 डायबिटीज़ (शुगर) के इलाज के लिए।',
  standard_adult_dose_hi = 'दिन में 2 बार 500-1000 मि.ग्रा., खाने के साथ',
  common_side_effects_hi = 'मतली, दस्त, पेट दर्द, मुंह में धातु जैसा स्वाद',
  contraindications_hi = 'गंभीर किडनी रोग या लीवर रोग में न लें। सर्जरी से पहले रोकें।',
  alcohol_warning_hi = 'शराब से बचें — लैक्टिक एसिडोसिस का खतरनाक खतरा।'
WHERE generic_name = 'metformin';

-- 21. METRONIDAZOLE
UPDATE drugs SET
  common_uses_simple_hi = 'पेट के संक्रमण, अमीबा, और कुछ स्त्री रोग के लिए।',
  standard_adult_dose_hi = 'दिन में 3 बार 400 मि.ग्रा. — पूरा कोर्स लें',
  common_side_effects_hi = 'मतली, मुंह में धातु स्वाद, सिरदर्द, गहरा पेशाब',
  contraindications_hi = 'पहली तिमाही गर्भावस्था में न लें। नर्व की समस्या में डॉक्टर से पूछें।',
  alcohol_warning_hi = 'शराब बिल्कुल न लें — गंभीर मतली, उल्टी, धड़कन तेज़ हो सकती है।'
WHERE generic_name = 'metronidazole';

-- 22. MONTELUKAST
UPDATE drugs SET
  common_uses_simple_hi = 'अस्थमा और एलर्जी के नियंत्रण के लिए।',
  standard_adult_dose_hi = 'रोज़ रात को एक बार 10 मि.ग्रा.',
  common_side_effects_hi = 'सिरदर्द, पेट दर्द, मूड में बदलाव, नींद की समस्या',
  contraindications_hi = 'मानसिक रोग का इतिहास हो तो डॉक्टर को बताएं।',
  alcohol_warning_hi = 'शराब सीमित करें — मूड पर असर बढ़ सकता है।'
WHERE generic_name = 'montelukast';

-- 23. OMEPRAZOLE
UPDATE drugs SET
  common_uses_simple_hi = 'पेट में अम्ल (एसिडिटी), गैस्ट्राइटिस, और अल्सर के लिए।',
  standard_adult_dose_hi = 'रोज़ एक बार 20 मि.ग्रा., खाने से पहले',
  common_side_effects_hi = 'सिरदर्द, दस्त, मतली, पेट में गैस',
  contraindications_hi = 'ओमेप्राज़ोल से एलर्जी हो तो न लें।',
  alcohol_warning_hi = 'शराब सीमित करें — एसिडिटी बढ़ सकती है।'
WHERE generic_name = 'omeprazole';

-- 24. PANTOPRAZOLE
UPDATE drugs SET
  common_uses_simple_hi = 'पेट में अम्ल (एसिडिटी), गैस्ट्राइटिस, और अल्सर के लिए।',
  standard_adult_dose_hi = 'रोज़ एक बार 40 मि.ग्रा., खाने से पहले',
  common_side_effects_hi = 'सिरदर्द, दस्त, मतली, पेट दर्द',
  contraindications_hi = 'पैंटोप्राज़ोल से एलर्जी हो तो न लें।',
  alcohol_warning_hi = 'शराब सीमित करें — एसिडिटी बढ़ सकती है।'
WHERE generic_name = 'pantoprazole';

-- 25. PARACETAMOL
UPDATE drugs SET
  common_uses_simple_hi = 'बुखार और हल्के-से-मध्यम दर्द के लिए।',
  standard_adult_dose_hi = 'हर 4-6 घंटे 500-1000 मि.ग्रा. (24 घंटे में 4 ग्राम से ज़्यादा नहीं)',
  common_side_effects_hi = 'सामान्य खुराक में दुष्प्रभाव कम। ज़्यादा लेने से लीवर खराब हो सकता है।',
  contraindications_hi = 'गंभीर लीवर रोग में न लें। 24 घंटे में 4 ग्राम से ज़्यादा कभी न लें।',
  alcohol_warning_hi = 'शराब से बचें — लीवर पर गंभीर असर पड़ सकता है।'
WHERE generic_name = 'paracetamol';

-- 26. RANITIDINE
UPDATE drugs SET
  common_uses_simple_hi = 'पेट में अम्ल और अल्सर के लिए। (नोट: कई देशों में बंद)',
  standard_adult_dose_hi = 'दिन में 2 बार 150 मि.ग्रा. या रात को 300 मि.ग्रा.',
  common_side_effects_hi = 'सिरदर्द, चक्कर, कब्ज़, दस्त',
  contraindications_hi = 'पोरफाइरिया या गंभीर किडनी रोग में न लें। डॉक्टर से विकल्प पूछें।',
  alcohol_warning_hi = 'शराब सीमित करें — एसिडिटी बढ़ सकती है।'
WHERE generic_name = 'ranitidine';

-- 27. ROSUVASTATIN
UPDATE drugs SET
  common_uses_simple_hi = 'खून में कोलेस्ट्रॉल कम करने के लिए।',
  standard_adult_dose_hi = 'रोज़ एक बार 5-20 मि.ग्रा.',
  common_side_effects_hi = 'मांसपेशियों में दर्द, सिरदर्द, पेट खराब, कमज़ोरी',
  contraindications_hi = 'गर्भावस्था, स्तनपान, या सक्रिय लीवर रोग में न लें।',
  alcohol_warning_hi = 'शराब सीमित करें — लीवर और मांसपेशियों पर असर बढ़ता है।'
WHERE generic_name = 'rosuvastatin';

-- 28. TELMISARTAN
UPDATE drugs SET
  common_uses_simple_hi = 'उच्च रक्तचाप (हाई बीपी) और हृदय की रक्षा के लिए।',
  standard_adult_dose_hi = 'रोज़ एक बार 40-80 मि.ग्रा.',
  common_side_effects_hi = 'चक्कर, पीठ दर्द, सर्दी जैसे लक्षण, पोटाशियम बढ़ना',
  contraindications_hi = 'गर्भावस्था में बिल्कुल न लें। पित्त की रुकावट में डॉक्टर से पूछें।',
  alcohol_warning_hi = 'शराब से बचें — बीपी बहुत कम हो सकता है।'
WHERE generic_name = 'telmisartan';