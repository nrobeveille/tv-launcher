package fr.nrobeveille.tvlauncher.flirt

import androidx.compose.ui.graphics.Color

/** Expressions des personnages. */
enum class Mood { NEUTRAL, SMILE, BLUSH, ANNOYED, LAUGH }

class Choice(val text: String, val heat: Int, val reaction: String, val mood: Mood)

class Round(val question: String, val choices: List<Choice>)

/** Apparence d'un personnage (dessiné, entièrement fictif). */
class Look(
    val skin: Color,
    val hair: Color,
    /** 0 = carré, 1 = longs bouclés, 2 = chignon */
    val hairStyle: Int,
    val top: Color,
    /** 0 = lunettes + sweat « </> », 1 = casque de DJ, 2 = tablier + barrette cerise */
    val accessory: Int,
)

class Encounter(val name: String, val role: String, val look: Look, val rounds: List<Round>)

/**
 * Love.exe : la soirée d'anniversaire du birthday boy. Trois rencontres, trois questions
 * chacune, et un thermomètre qui monte (ou pas). Tout public : au mieux un numéro ou un slow.
 */
object FlirtStory {

    const val START_HEAT = 30

    val encounters = listOf(
        Encounter(
            name = "Léa",
            role = "Dev back-end · 3e café de la soirée",
            look = Look(Color(0xFFF1C7A5), Color(0xFF3B2618), 0, Color(0xFF3D5A80), 0),
            rounds = listOf(
                Round(
                    "Salut ! C'est toi le birthday boy ? Joyeux anniv' !",
                    listOf(
                        Choice("Merci ! Tu es là pour le gâteau ou pour moi ?", 15,
                            "Pour le gâteau. Mais tu as tes chances de passer en deuxième.", Mood.LAUGH),
                        Choice("Tu savais que mon âge en binaire, c'est super joli ?", 25,
                            "Attends… tu parles en binaire ? Continue, ça m'intéresse.", Mood.BLUSH),
                        Choice("Ouais. Tu m'as ramené un cadeau ?", -15,
                            "Euh… j'ai ramené ma présence. C'est déjà pas mal.", Mood.ANNOYED),
                    ),
                ),
                Round(
                    "Et sinon, tu fais quoi dans la vie ?",
                    listOf(
                        Choice("Je corrige des bugs. Le plus gros ce soir : tu n'es pas encore assise à côté de moi.", 25,
                            "…Celle-là était nulle. Mais je m'assois quand même.", Mood.BLUSH),
                        Choice("Je code des trucs qui marchent du premier coup.", -10,
                            "Menteur. Personne ne fait ça.", Mood.ANNOYED),
                        Choice("Ce soir ? Je fais surtout la fête.", 10,
                            "Bonne réponse. Le taf, c'est lundi.", Mood.SMILE),
                    ),
                ),
                Round(
                    "Question piège : tabulations ou espaces ?",
                    listOf(
                        Choice("Espaces. Comme ça on garde nos distances… mais pas trop.", 20,
                            "Habile. Très habile.", Mood.SMILE),
                        Choice("Tabulations, évidemment.", 25,
                            "Moi aussi ! On est faits pour s'entendre.", Mood.BLUSH),
                        Choice("Je m'en fiche, je code tout sur une seule ligne.", -20,
                            "Tu viens de perdre tout mon respect. Et ma confiance.", Mood.ANNOYED),
                    ),
                ),
            ),
        ),
        Encounter(
            name = "Sarah",
            role = "DJ de la soirée",
            look = Look(Color(0xFFC68B59), Color(0xFF1C1412), 1, Color(0xFF8E44AD), 1),
            rounds = listOf(
                Round(
                    "Hey ! C'est ta soirée : je te passe quoi ?",
                    listOf(
                        Choice("Un truc qui donne envie de danser… avec toi.", 25,
                            "Oh ! Alors slow dans dix minutes. Sois prêt.", Mood.BLUSH),
                        Choice("Le son de démarrage de mon PC.", 5,
                            "Original. Le public va adorer… ou pas du tout.", Mood.LAUGH),
                        Choice("Rien, je préfère le silence.", -20,
                            "…Tu as compris que j'étais DJ ?", Mood.ANNOYED),
                    ),
                ),
                Round(
                    "Et toi, tu danses bien ?",
                    listOf(
                        Choice("Comme un robot aspirateur : je me cogne partout mais je reviens toujours.", 20,
                            "Ha ha ! J'ai hâte de voir ça.", Mood.LAUGH),
                        Choice("Je suis le meilleur danseur de la pièce.", -5,
                            "On va vérifier. Je sors mon téléphone, je filme.", Mood.NEUTRAL),
                        Choice("Pas trop… mais avec une bonne prof, j'apprends vite.", 25,
                            "Hmm. J'accepte le poste.", Mood.BLUSH),
                    ),
                ),
                Round(
                    "Ça y est, le slow commence. Tu fais quoi ?",
                    listOf(
                        Choice("Je t'invite à danser, évidemment.", 25,
                            "Allez, viens. Mais si tu me marches sur les pieds, c'est fini.", Mood.BLUSH),
                        Choice("Je vais me resservir du gâteau.", -15,
                            "Le gâteau. Toujours le gâteau.", Mood.ANNOYED),
                        Choice("Je lance une mise à jour pendant le slow.", -20,
                            "Redémarrage en cours… de ma patience.", Mood.ANNOYED),
                    ),
                ),
            ),
        ),
        Encounter(
            name = "Camille",
            role = "A fait le gâteau d'anniversaire",
            look = Look(Color(0xFFF7D9C4), Color(0xFFB5552B), 2, Color(0xFFF4F1E8), 2),
            rounds = listOf(
                Round(
                    "Alors, il est bon mon gâteau ?",
                    listOf(
                        Choice("Délicieux. Presque autant que la pâtissière.", 20,
                            "Flatteur… Tu as de la crème sur le nez, au passage.", Mood.LAUGH),
                        Choice("Il manque un peu de sucre.", -20,
                            "Ah. Pas de deuxième part pour toi, alors.", Mood.ANNOYED),
                        Choice("Je peux en reprendre trois parts ?", 15,
                            "Un homme qui aime mes gâteaux, c'est un bon début.", Mood.SMILE),
                    ),
                ),
                Round(
                    "Tu as fait un vœu en soufflant les bougies ?",
                    listOf(
                        Choice("Oui. Et je crois qu'il est en train de se réaliser.", 25,
                            "…Arrête, je vais rougir.", Mood.BLUSH),
                        Choice("Que mon code compile du premier coup.", 10,
                            "Tu rêves grand, toi.", Mood.LAUGH),
                        Choice("Non, j'ai oublié de souffler.", -10,
                            "C'est pour ça que l'alarme incendie a sonné ?", Mood.NEUTRAL),
                    ),
                ),
                Round(
                    "Il est tard… Tu me raccompagnes jusqu'au tram ?",
                    listOf(
                        Choice("Avec plaisir. Et je porte le reste du gâteau.", 25,
                            "Gentleman ET gourmand. Validé.", Mood.BLUSH),
                        Choice("Je peux pas, je dois finir ma partie de Debug Road.", -15,
                            "Le canard passe avant moi ? Sérieusement ?", Mood.ANNOYED),
                        Choice("Seulement si tu me promets un autre gâteau.", 15,
                            "Marché conclu.", Mood.SMILE),
                    ),
                ),
            ),
        ),
    )

    class Outcome(val title: String, val text: String, val mood: Mood, val good: Boolean)

    fun outcome(heat: Int, name: String): Outcome = when {
        heat >= 85 -> Outcome("Ça chauffe !", "$name te laisse son numéro… et te fait un bisou sur la joue.", Mood.BLUSH, true)
        heat >= 55 -> Outcome("Pas mal du tout", "$name te fait la bise et te souhaite un très bon anniversaire.", Mood.SMILE, true)
        else -> Outcome("Râteau.exe a cessé de fonctionner", "$name est partie rejoindre le buffet. Sans toi.", Mood.ANNOYED, false)
    }

    fun finalTitle(total: Int): String = when {
        total >= 240 -> "Roi de la soirée"
        total >= 170 -> "Charmeur en version bêta"
        else -> "Râteau en production"
    }
}
