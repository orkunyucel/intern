SELECT 
    SenderAddress,
    CASE 
        WHEN 
            -- 1. Length Check (Java: senderAddress.length() < 3 || senderAddress.length() > 11)
            (LEN(SenderAddress) < 3 OR LEN(SenderAddress) > 11)
            OR
            -- 2. Leading/Trailing Space Check (Java: senderAddress.length() != senderAddress.trim().length())
            -- LEN() sondaki boslugu yutar, bu yuzden LIKE ile kontrol ediyoruz
            (SenderAddress LIKE ' %' OR SenderAddress LIKE '% ')
            OR
            -- 3. Double Space Check (Java: senderAddress.contains("  "))
            (SenderAddress LIKE '%  %')
        THEN 'INVALID'

        -- 4. Numeric Logic Branch (Java: isNumber -> chars().allMatch(Character::isDigit))
        WHEN SenderAddress NOT LIKE '%[^0-9]%' THEN 
            CASE 
                WHEN LEN(SenderAddress) = 3 
                     AND LEFT(SenderAddress, 1) = '1' 
                     AND SUBSTRING(SenderAddress, 2, 1) <> '0' 
                THEN 'VALID'
                ELSE 'INVALID'
            END

        -- 5. Alphanumeric Check (Java: Character.isLetterOrDigit(c) || c == ' ')
        -- Java Unicode destekler, bu yuzden Turkce karakterler de eklendi
        WHEN SenderAddress LIKE N'%[^a-zA-Z0-9 çÇğĞıİöÖüÜşŞ]%' THEN 'INVALID'

        -- 6. Suspicious Characters (Java: suspiciousChars.indexOf(c) >= 0 — Case Sensitive)
        -- Java case-sensitive calisir, SQL icin COLLATE CS (Case Sensitive) eklendi
        WHEN SenderAddress COLLATE Latin1_General_CS_AS NOT LIKE '%[^OoIlEASsbTBqg0123456789]%' THEN 'INVALID'

        ELSE 'VALID'
    END AS Status
FROM 
    SenderTable
ORDER BY 
    Status;

