package mca.client.gui;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import mca.client.gui.widget.ColorPickerWidget;
import mca.client.gui.widget.GeneSliderWidget;
import mca.cobalt.network.NetworkHandler;
import mca.entity.EntitiesMCA;
import mca.entity.Infectable;
import mca.entity.VillagerEntityMCA;
import mca.entity.ai.Genetics;
import mca.entity.ai.Memories;
import mca.entity.ai.ProfessionsMCA;
import mca.entity.ai.relationship.AgeState;
import mca.entity.ai.relationship.Gender;
import mca.entity.ai.relationship.Personality;
import mca.network.VillagerEditorSyncRequest;
import mca.network.getVillagerRequest;
import mca.resources.API;
import mca.resources.ClothingList;
import mca.resources.data.Hair;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.village.VillagerProfession;

import static mca.entity.VillagerLike.VILLAGER_NAME;

public class VillagerEditorScreen extends Screen {
    private final UUID villagerUUID;
    private NbtCompound villagerData;
    private int villagerBreedingAge;
    private String page;
    private final VillagerEntityMCA villager = EntitiesMCA.MALE_VILLAGER.create(MinecraftClient.getInstance().world);
    private static int DATA_WIDTH = 150;

    public VillagerEditorScreen(UUID villagerUUID) {
        super(new TranslatableText("gui.VillagerEditorScreen.title"));
        this.villagerUUID = villagerUUID;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void init() {
        requestVillagerData();
        setPage("loading");
    }

    private int doubleGeneSliders(int y, Genetics.GeneType... genes) {
        boolean right = false;
        assert villager != null;
        Genetics genetics = villager.getGenetics();
        for (Genetics.GeneType g : genes) {
            addButton(new GeneSliderWidget(width / 2 + (right ? DATA_WIDTH / 2 : 0), y, DATA_WIDTH / 2, 20, new TranslatableText(g.getTranslationKey()), genetics.getGene(g), b -> genetics.setGene(g, b.floatValue())));
            if (right) {
                y += 20;
            }
            right = !right;
        }
        return y + 4 + (right ? 20 : 0);
    }

    private int integerChanger(int y, Consumer<Integer> onClick, Supplier<Text> content) {
        int bw = 22;
        ButtonWidget current = addButton(new ButtonWidget(width / 2 + bw * 2, y, DATA_WIDTH - bw * 4, 20, content.get(), b -> {
        }));
        addButton(new ButtonWidget(width / 2, y, bw, 20, new LiteralText("-1"), b -> {
            onClick.accept(-1);
            current.setMessage(content.get());
        }));
        addButton(new ButtonWidget(width / 2 + bw, y, bw, 20, new LiteralText("-10"), b -> {
            onClick.accept(-10);
            current.setMessage(content.get());
        }));
        addButton(new ButtonWidget(width / 2 + DATA_WIDTH - bw * 2, y, bw, 20, new LiteralText("+10"), b -> {
            onClick.accept(10);
            current.setMessage(content.get());
        }));
        addButton(new ButtonWidget(width / 2 + DATA_WIDTH - bw, y, bw, 20, new LiteralText("+1"), b -> {
            onClick.accept(1);
            current.setMessage(content.get());
        }));
        return y + 22;
    }

    private void setPage(String page) {
        assert villager != null;
        this.page = page;

        buttons.clear();
        children.clear();

        //page selection
        String[] pages = {"general", "body", "head", "personality", "debug"};
        int w = DATA_WIDTH * 2 / pages.length;
        int x = (int)(width / 2.0 - pages.length / 2.0 * w);
        for (String p : pages) {
            addButton(new ButtonWidget(x, height / 2 - 105, w, 20, new TranslatableText(p), sender -> {
                setPage(p);
            })).active = !p.equals(page);
            x += w;
        }

        //close
        addButton(new ButtonWidget(width / 2 - DATA_WIDTH + 20, height / 2 + 85, DATA_WIDTH - 40, 20, new TranslatableText("gui.done"), sender -> {
            syncVillagerData();
            onClose();
        }));

        int y = height / 2 - 80;
        int margin = 40;
        Genetics genetics = villager.getGenetics();

        switch (page) {
            case "general":
                //name
                TextFieldWidget field = addButton(new TextFieldWidget(this.textRenderer, width / 2, y, DATA_WIDTH, 20, new TranslatableText("structure_block.structure_name")));
                field.setMaxLength(32);
                field.setText(villager.getDefaultName().asString());
                field.setChangedListener(name -> villager.setTrackedValue(VILLAGER_NAME, name));
                //TODO update familyTree entry
                y += 22;

                //gender
                addButton(new ButtonWidget(width / 2, y, DATA_WIDTH / 2, 20, new TranslatableText("gui.villager_editor.female"), sender -> {
                    villager.getGenetics().setGender(Gender.FEMALE);
                }));
                addButton(new ButtonWidget(width / 2 + DATA_WIDTH / 2, y, DATA_WIDTH / 2, 20, new TranslatableText("gui.villager_editor.male"), sender -> villager.getGenetics().setGender(Gender.MALE)));
                y += 22;

                //age
                addButton(new GeneSliderWidget(width / 2, y, DATA_WIDTH, 20, new TranslatableText("gui.villager_editor.age"), 1.0 - villagerBreedingAge / (double)AgeState.MAX_AGE, b -> {
                    villagerBreedingAge = (int)((1.0 - b) * AgeState.MAX_AGE);
                    villager.setBreedingAge(villagerBreedingAge);
                }));
                y += 22;
                break;
            case "body":
                //genes
                y = doubleGeneSliders(y, Genetics.SIZE, Genetics.WIDTH, Genetics.BREAST, Genetics.SKIN);

                //clothes name
                field = addButton(new TextFieldWidget(this.textRenderer, width / 2, y, DATA_WIDTH, 20, new TranslatableText("structure_block.structure_name")));
                field.setMaxLength(32);
                field.setText(villager.getClothes());
                field.setChangedListener(villager::setClothes);
                y += 22;

                //clothes
                addButton(new ButtonWidget(width / 2, y, DATA_WIDTH / 2, 20, new TranslatableText("gui.villager_editor.prevClothing"), b -> {
                    villager.setClothes(ClothingList.getInstance().getPool(villager).pickNext(villager.getClothes(), -1));
                    field.setText(villager.getClothes());
                }));
                addButton(new ButtonWidget(width / 2 + DATA_WIDTH / 2, y, DATA_WIDTH / 2, 20, new TranslatableText("gui.villager_editor.nextClothing"), b -> {
                    villager.setClothes(ClothingList.getInstance().getPool(villager).pickNext(villager.getClothes(), 1));
                    field.setText(villager.getClothes());
                }));
                y += 22;

                //skin color
                addButton(new ColorPickerWidget(width / 2 + margin, y, DATA_WIDTH - margin * 2, DATA_WIDTH - margin * 2,
                        genetics.getGene(Genetics.HEMOGLOBIN),
                        genetics.getGene(Genetics.MELANIN),
                        new Identifier("mca:textures/colormap/villager_skin.png"),
                        (vx, vy) -> {
                            genetics.setGene(Genetics.HEMOGLOBIN, vx.floatValue());
                            genetics.setGene(Genetics.MELANIN, vy.floatValue());
                        }));
                y += DATA_WIDTH - margin * 2;

                break;
            case "head":
                //genes
                y = doubleGeneSliders(y, Genetics.FACE);

                //hair name
                field = addButton(new TextFieldWidget(this.textRenderer, width / 2, y, DATA_WIDTH, 20, new TranslatableText("structure_block.structure_name")));
                field.setMaxLength(32);
                field.setText(villager.getHair().texture());
                field.setChangedListener(name -> {
                    villager.setHair(new Hair(name, villager.getHair().overlay()));
                });
                y += 22;

                TextFieldWidget field2 = addButton(new TextFieldWidget(this.textRenderer, width / 2, y, DATA_WIDTH, 20, new TranslatableText("structure_block.structure_name")));
                field2.setMaxLength(32);
                field2.setText(villager.getHair().overlay());
                field2.setChangedListener(name -> {
                    villager.setHair(new Hair(villager.getHair().texture(), name));
                });
                y += 22;

                //hair
                addButton(new ButtonWidget(width / 2, y, DATA_WIDTH / 2, 20, new TranslatableText("gui.villager_editor.prevHair"), b -> {
                    villager.setHair(API.getHairPool().pickNext(villager, villager.getHair(), -1));
                    field.setText(villager.getHair().texture());
                    field2.setText(villager.getHair().overlay());
                }));
                addButton(new ButtonWidget(width / 2 + DATA_WIDTH / 2, y, DATA_WIDTH / 2, 20, new TranslatableText("gui.villager_editor.nextHair"), b -> {
                    villager.setHair(API.getHairPool().pickNext(villager, villager.getHair(), 1));
                    field.setText(villager.getHair().texture());
                    field2.setText(villager.getHair().overlay());
                }));
                y += 22;

                //hair color
                addButton(new ColorPickerWidget(width / 2 + margin, y, DATA_WIDTH - margin * 2, DATA_WIDTH - margin * 2,
                        genetics.getGene(Genetics.PHEOMELANIN),
                        genetics.getGene(Genetics.EUMELANIN),
                        new Identifier("mca:textures/colormap/villager_hair.png"),
                        (vx, vy) -> {
                            genetics.setGene(Genetics.PHEOMELANIN, vx.floatValue());
                            genetics.setGene(Genetics.EUMELANIN, vy.floatValue());
                        }));
                y += DATA_WIDTH - margin * 2;
                break;
            case "personality":
                //personality
                List<ButtonWidget> personalityButtons = new LinkedList<>();
                int row = 0;
                for (Personality p : Personality.values()) {
                    if (p != Personality.UNASSIGNED) {
                        if (row == 3) {
                            row = 0;
                            y += 20;
                        }
                        ButtonWidget widget = addButton(new ButtonWidget(width / 2 + DATA_WIDTH / 3 * row, y, DATA_WIDTH / 3, 20, p.getName(), b -> {
                            villager.getVillagerBrain().setPersonality(p);
                            personalityButtons.forEach(v -> v.active = true);
                            b.active = false;
                        }));
                        widget.active = p != villager.getVillagerBrain().getPersonality();
                        personalityButtons.add(widget);
                        row++;
                    }
                }
                y += 22;
                break;
            case "debug":
                //profession
                boolean right = false;
                List<ButtonWidget> professionButtons = new LinkedList<>();
                for (VillagerProfession p : new VillagerProfession[] {
                        VillagerProfession.NONE,
                        ProfessionsMCA.GUARD,
                        ProfessionsMCA.ARCHER,
                        ProfessionsMCA.OUTLAW,
                }) {
                    TranslatableText text = new TranslatableText("entity.minecraft.villager." + p);
                    ButtonWidget widget = addButton(new ButtonWidget(width / 2 + (right ? DATA_WIDTH / 2 : 0), y, DATA_WIDTH / 2, 20, text, b -> {
                        NbtCompound compound = new NbtCompound();
                        compound.putString("profession", Registry.VILLAGER_PROFESSION.getId(p).toString());
                        syncVillagerData();
                        NetworkHandler.sendToServer(new VillagerEditorSyncRequest("profession", villagerUUID, compound));
                        requestVillagerData();
                        professionButtons.forEach(button -> button.active = true);
                        b.active = false;
                    }));
                    professionButtons.add(widget);
                    widget.active = villager.getProfession() != p;
                    if (right) {
                        y += 20;
                    }
                    right = !right;
                }
                y += 4;

                //infection
                addButton(new GeneSliderWidget(width / 2, y, DATA_WIDTH, 20, new TranslatableText("gui.villager_editor.infection"), villager.getInfectionProgress() / Infectable.MAX_INFECTION, b -> {
                    villager.setInfected(b > 0);
                    villager.setInfectionProgress(b.floatValue() * Infectable.MAX_INFECTION);
                }));
                y += 22;

                //hearts
                Memories player = villager.getVillagerBrain().getMemoriesForPlayer(client.player);
                y = integerChanger(y, player::modHearts, () -> new LiteralText(player.getHearts() + " hearts"));

                //mood
                y = integerChanger(y, v -> villager.getVillagerBrain().modifyMoodValue(v), () -> new LiteralText(villager.getVillagerBrain().getMoodValue() + " mood"));
                break;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);

        fill(matrices, 0, 20, width, height - 20, 0x66000000);

        if (villager == null) {
            return;
        }

        InventoryScreen.drawEntity(width / 2 - DATA_WIDTH / 2, height / 2 + 70, 60, 0, 0, villager);

        super.render(matrices, mouseX, mouseY, delta);
    }

    public void setVillagerData(NbtCompound data) {
        villagerData = data.getCompound(villagerUUID.toString());
        if (villager != null) {
            villager.readCustomDataFromNbt(villagerData);
            villagerBreedingAge = villagerData.getInt("Age");
        }
        if (page.equals("loading")) {
            setPage("general");
        }
    }

    private void requestVillagerData() {
        NetworkHandler.sendToServer(new getVillagerRequest(villagerUUID));
    }

    private void syncVillagerData() {
        assert villager != null;
        NbtCompound nbt = new NbtCompound();
        ((MobEntity)villager).writeCustomDataToNbt(nbt);
        nbt.putInt("Age", villagerBreedingAge);
        NetworkHandler.sendToServer(new VillagerEditorSyncRequest("sync", villagerUUID, nbt));
    }
}